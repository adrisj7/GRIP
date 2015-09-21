package edu.wpi.gripgenerator.settings;

import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import edu.wpi.gripgenerator.templates.SocketHintDeclaration;

import java.util.*;

public class DefinedMethod {
    private final String methodName;
    private final List<DefinedParamType> paramTypes;
    private final boolean isCompleteList;
    private DefinedMethodCollection collectionOf;
    private Optional<String> description = Optional.empty();
    private Optional<MethodDeclaration> bestMatchMethod = Optional.empty();
    private boolean finalized = false;


    /**
     * Constructs the defined method with it assumed to be the complete list
     * @param methodName
     * @param paramTypes
     */
    public DefinedMethod(String methodName, String ...paramTypes){
        this(methodName, false, paramTypes);
    }

    public DefinedMethod(String methodName){
        this(methodName, false, new ArrayList());
    }

    public DefinedMethod(String methodName, boolean isCompleteList, String ...paramTypes){
        this(methodName, isCompleteList, Arrays.asList(paramTypes));
    }

    public DefinedMethod(String methodName, boolean isCompleteList, List<String> paramTypes){
        this.methodName = methodName;
        this.paramTypes = DefinedParamType.fromStrings(paramTypes);
        this.isCompleteList = isCompleteList;
    }

    void setCollectionOf(DefinedMethodCollection collection){
        this.collectionOf = collection;
    }

    public String getMethodName(){
        return methodName;
    }

    public DefinedMethod addDescription(String description){
        this.description = Optional.of(description);
        return this;
    }

    /**
     * Is this defined method a match to the params of another MethodDeclaration's params
     * @param params
     * @return true if a match
     */
    public boolean isMatch(List<Parameter> params){
        // Check if the last param was a wildcard
        if(this.isCompleteList && paramTypes.size() != params.size()) return false;
        else if(paramTypes.size() > params.size()) return false;

        for(int i = 0; i < paramTypes.size(); i++){
            if(!paramTypes.get(i).isMatch(params.get(i))) return false;
        }
        return true;
    }


    public boolean assignIfBestMatch(MethodDeclaration declaration) {
        assert !finalized : "Assigning on a method that has already been finalized";
        if (!isMatch(declaration.getParameters())) {
            //TODO: Perhaps this should throw an error?
            return false;
        }
        if (this.bestMatchMethod.isPresent()) {
            if (this.bestMatchMethod.get().getParameters().size() > declaration.getParameters().size()) {
                // The method we have is already more complex
                return false;
            }
        }
        this.bestMatchMethod = Optional.of(declaration);
        return true;
    }

    protected boolean finalizeParamTypes(){
        if(finalized) return true;
        this.finalized = true;
        if(bestMatchMethod.isPresent()){
            final MethodDeclaration declaration = bestMatchMethod.get();

            // Iterate over the chosen methods parameters
            for(int i = 0; i < declaration.getParameters().size(); i++){
                Parameter param = declaration.getParameters().get(i);
                DefinedParamType definedParamType = null;
                assert i < paramTypes.size() : "The size of the params list was less than than the index. Invalid state";
                if (i == paramTypes.size()) {
                    definedParamType = new DefinedParamType(param.getType().toString());
                    paramTypes.add(definedParamType);
                } else {
                    definedParamType = paramTypes.get(i);
                }
                definedParamType.setParamAs(param);
                if(collectionOf.isOutputDefault(param.getId().getName())){
                    definedParamType.setOutput();
                }
            }
            return this.finalized;
        } else {
            //TODO: Throw an error?
            System.err.println("No method was found that matched this method definition " + toSimpleString());
            this.finalized = false;
            return this.finalized;
        }
    }

    public List<SocketHintDeclaration> getSocketHints(){
        if(!this.finalizeParamTypes()){ return null; }
        List<SocketHintDeclaration> socketHintDeclarations = new ArrayList<>();
        Map<Type, List<DefinedParamType>> inputHintsMap = new HashMap<>();
        Map<Type, List<DefinedParamType>> outputHintsMap = new HashMap<>();

        // Figure out which hint map to put this defined param type into
        for(DefinedParamType type : paramTypes){
            Map<Type, List<DefinedParamType>> assignmentMap;
            assignmentMap = type.isOutput() ? outputHintsMap : inputHintsMap;
            assignmentMap.putIfAbsent(type.getType(), new ArrayList<>()); // Will return null if new
            assignmentMap.get(type.getType()).add(type);
        }

        for(Type type : inputHintsMap.keySet()){
            socketHintDeclarations.add(new SocketHintDeclaration(type, inputHintsMap.get(type)));
        }
        for(Type type : outputHintsMap.keySet()){
            socketHintDeclarations.add(new SocketHintDeclaration(type, outputHintsMap.get(type)));
        }

        return socketHintDeclarations;
    }

    public String toSimpleString(){
        return "DefinedMethod{" +
                "methodName='" + methodName + '\'' +
                ", paramTypes=" + paramTypes +
                ", isCompleteList=" + isCompleteList +
                ", collectionOf=" + collectionOf +
                ", description=" + description +
                '}';
    }
}
