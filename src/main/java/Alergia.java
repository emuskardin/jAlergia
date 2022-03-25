import java.util.*;

import static java.lang.Math.*;

enum ModelType{
    MDP,
    SMM,
    MC,
}

enum OptimizeFor{
    MEMORY,
    ACCURACY
}

class ParentInputPair {
    FptaNode parent;
    String inputOutput;
    public ParentInputPair(FptaNode p, String io){
        parent = p;
        inputOutput = io;
    }
}

class FptaNode{
    public static HashMap<String, String> stringCache = new HashMap<>();

    public String output;
    public Map<String, FptaNode> children;
    public Map<String, Integer> inputFrequency;
    public ParentInputPair parentInputPair;

    // for writing to file
    public String stateId;
    public Map<String, Double> childrenProbability;

    public FptaNode(String o){
        this.output = o;
        this.children = new TreeMap<>();
        this.inputFrequency = new TreeMap<>();
    }

    static String getFromStrCache(String str){
        FptaNode.stringCache.putIfAbsent(str, str);
        return FptaNode.stringCache.get(str);
    }

    public List<String> getPrefix(){
        List<String> prefix = new ArrayList<>();
        FptaNode p = this;
        while (p.parentInputPair.parent != null) {
            prefix.add(0, p.parentInputPair.inputOutput);
            p = p.parentInputPair.parent;
        }
        return prefix;
    }

    public Collection<? extends FptaNode> getSuccessors() {
        return this.children.values();
    }
}



public class Alergia {

    private FptaNode t = null;
    private FptaNode a = null;
    private final double epsilon;
    private final ModelType modelType;
    private final String filePath;
    private final String saveLocation;
    private final OptimizeFor optimizeFor;

    public Alergia(String pathToFile, double eps, ModelType type, String saveFile, OptimizeFor optim){
        modelType = type;
        epsilon = eps;
        saveLocation = saveFile;
        optimizeFor = optim;
        filePath = pathToFile;
    }

    public void runAlergia(){
        double start = System.currentTimeMillis();
        List<FptaNode> ta = Parser.parseFile(filePath, modelType, optimizeFor);
        double timeElapsed = System.currentTimeMillis() - start;
        System.out.println("FPTA construction time   : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        a = ta.get(0);
        t = ta.get(1);
        start = System.currentTimeMillis();
        int modelSize = run();
        timeElapsed = System.currentTimeMillis() - start;
        System.out.println("Alergia learning time    : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        System.out.println("Alergia learned " + modelSize + " state automaton.");
    }

    private int run() {
        List<FptaNode> red = new ArrayList<>();
        red.add(a);
        List<FptaNode> blue = new ArrayList<>(a.getSuccessors());

        while (!blue.isEmpty()){
            FptaNode lexMinBlue = getLexMin(blue);
            boolean merged = false;

            for (FptaNode r : red){
                if(compatibilityTest(getBlueNode(r), getBlueNode(lexMinBlue))){
                    merge(r, lexMinBlue);
                    merged = true;
                    break;
                }
            }

            if(!merged)
                insertInLexMinSort(red, lexMinBlue);

            blue.clear();
            Set<List<String>> prefixesInRed = new HashSet<>();
            for(FptaNode r:red)
                prefixesInRed.add(r.getPrefix());

            for(FptaNode r:red){
                for (FptaNode s : r.getSuccessors()){
                    if(!prefixesInRed.contains(s.getPrefix()))
                        blue.add(s);
                }
            }
        }

        normalize(red);
        Parser.saveModel(red, modelType, saveLocation);
        return red.size();
    }

    private void merge(FptaNode r, FptaNode lexMinBlue) {
        FptaNode blueNode = getBlueNode(lexMinBlue);
        List<String> prefixLeadingToState = new ArrayList<>(lexMinBlue.getPrefix());
        String lastIo = prefixLeadingToState.remove(prefixLeadingToState.size() - 1);

        FptaNode toUpdate = a;
        for (String p : prefixLeadingToState)
            toUpdate = toUpdate.children.get(p);

        toUpdate.children.put(lastIo, r);

        fold(r, lexMinBlue, blueNode);
    }

    private void fold(FptaNode red, FptaNode blue, FptaNode blueTreeNode) {
        for (String io : blueTreeNode.children.keySet()){
            if(red.children.containsKey(io)){
                red.inputFrequency.put(io, red.inputFrequency.get(io) + blue.inputFrequency.get(io));
                fold(red.children.get(io), blue.children.get(io), blueTreeNode.children.get(io));
            }else{
                red.children.put(io, blue.children.get(io));
                red.inputFrequency.put(io, blueTreeNode.inputFrequency.get(io));
            }
        }
    }

    private boolean compatibilityTest(FptaNode a, FptaNode b){
        if(modelType != ModelType.SMM && !a.output.equals(b.output))
            return false;

        if(a.children.values().isEmpty() || b.children.values().isEmpty())
            return true;

        if(!checkDifference(a,b))
            return false;

        Set<String> intersection  = new HashSet<>(a.children.keySet());
        intersection.retainAll(b.children.keySet());
        for (String child : intersection){
            if(!compatibilityTest(a.children.get(child), b.children.get(child)))
                return false;
        }

        return true;
    }

    private boolean checkDifference(FptaNode a, FptaNode b) {
        int n1 = a.inputFrequency.values().stream().mapToInt(Integer::intValue).sum();
        int n2 = b.inputFrequency.values().stream().mapToInt(Integer::intValue).sum();

        if(n1 > 0 && n2 > 0){
            Set<String> aChildren= a.children.keySet();
            Set<String> bChildren= b.children.keySet();

            Set<String> union = new HashSet<>(aChildren);
            union.addAll(bChildren);
            for(String o : union){
               double aFreq = a.inputFrequency.getOrDefault(o, 0);
               double bFreq = b.inputFrequency.getOrDefault(o, 0);

               if(abs(aFreq / n1 - bFreq / n2) > ((sqrt(1./n1) + sqrt(1./n2)) * sqrt(0.5 * log(2 / epsilon))))
                   return false;
            }
        }

        return true;
    }

    private void insertInLexMinSort(List<FptaNode> redList, FptaNode blue){
        int index = 0;
        for (FptaNode r : redList){
            if(r.getPrefix().size() < blue.getPrefix().size()){
                index += 1;
            }else{
                break;
            }
        }
        redList.add(index, blue);
    }

    private FptaNode getLexMin(List<FptaNode> x){
        FptaNode min = x.get(0);
        for (FptaNode node: x) {
            if(node.getPrefix().size() < min.getPrefix().size())
                min = node;
        }
        return min;
    }

    private FptaNode getBlueNode(FptaNode redNode){
        if(optimizeFor == OptimizeFor.MEMORY)
            return redNode;

        FptaNode blueNode = t;
        for(String p : redNode.getPrefix())
            blueNode = blueNode.children.get(p);
        return blueNode;
    }


    private void normalize(List<FptaNode> red) {
        int index = 0;
        for(FptaNode r : red){
            r.stateId = "q" + index;
            index += 1;
            r.childrenProbability = new HashMap<>();

            if(modelType == ModelType.MC){
                int totalOutput = r.inputFrequency.values().stream().mapToInt(Integer::intValue).sum();
                for (String io : r.inputFrequency.keySet())
                    r.childrenProbability.put(io, r.inputFrequency.get(io).doubleValue() / totalOutput);
            }else{
                HashMap<String, Double> outputsPerInput = new HashMap<>();
                for (String io : r.inputFrequency.keySet()) {
                    List<String> inputAndOutput = Arrays.asList(io.split("/"));
                    outputsPerInput.put(inputAndOutput.get(0), r.inputFrequency.get(io) +
                            outputsPerInput.getOrDefault(inputAndOutput.get(0), 0.));
                }
                for (String io : r.inputFrequency.keySet()) {
                    List<String> inputAndOutput = Arrays.asList(io.split("/"));
                    r.childrenProbability.put(io, r.inputFrequency.get(io) / outputsPerInput.get(inputAndOutput.get(0)));
                }
            }
        }
    }

    public static void usageExample(){
        String path = "sampleFiles/mdpData2.txt";
        double eps = 0.005;
        ModelType type = ModelType.MDP;
        String saveLocation = "jAlergiaModel";
        OptimizeFor optimizeFor = OptimizeFor.ACCURACY;

        Alergia a = new Alergia(path, eps, type, saveLocation, optimizeFor);
        a.runAlergia();
    }

    public static void main(String[] args) {
        List<Object> argValues = Parser.parseArgs(args);
        String path = (String) argValues.get(0);
        double eps = (Double) argValues.get(1);
        ModelType type = (ModelType) argValues.get(2);
        String saveLocation = (String) argValues.get(3);
        OptimizeFor optimizeFor = (OptimizeFor) argValues.get(4);

        Alergia a = new Alergia(path, eps, type, saveLocation, optimizeFor);
        a.runAlergia();
        System.exit(0);
    }
}