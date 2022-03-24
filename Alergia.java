import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static java.lang.Math.*;

enum ModelType{
    MDP,
    SMM,
    MC,
}

class ParentInput{
    FptaNode parent;
    String inputOutput;
    public ParentInput(FptaNode p, String io){
        parent = p;
        inputOutput = io;
    }
}

class FptaNode{
    public static HashMap<String, String> stringCache = new HashMap<>();

    public String output;
    public Map<String, FptaNode> children;
    public Map<String, Integer> inputFrequency;
    public ParentInput parentInputPair;
    // for visualization
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

class Parser{

    public static List<FptaNode> parseFile(String path, ModelType modelType){
        FptaNode rootNode = null;
        FptaNode rootCopy = null;
        boolean notInit = true;

        try {
            List<String> fileLines = Files.readAllLines(Paths.get(path));
            for (String line : fileLines) {
                if(line.isEmpty())
                    continue;
                List<String> sample = Arrays.asList(line.split(","));
                if(notInit) {
                    FptaNode.getFromStrCache(sample.get(0));
                    rootNode = new FptaNode(sample.get(0));
                    rootNode.parentInputPair = new ParentInput(null, null);
                    rootCopy = new FptaNode(sample.get(0));
                    rootCopy.parentInputPair = new ParentInput(null, null);
                    notInit = false;
                }
                addToFpta(rootNode, rootCopy, sample, modelType);

            }
        } catch (IOException e) {
            System.out.println("File could not be opened.");
            e.printStackTrace();
        }
        return Arrays.asList(rootNode, rootCopy);
    }

    public static List<Object> parseArgs(String[] args){
        double eps = 0.005;
        ModelType type = ModelType.MDP;
        String path = null;
        String saveLocation = "jAlergiaModel";

        HashSet<String> argNames = new HashSet<>(Arrays.asList("-eps", "-path", "-type", "-save"));
        for (int i = 0; i < args.length - 1; i+=2) {
            if(!argNames.contains(args[i]))
                System.exit(1);

            if(args[i].equals("-eps")){
                try {
                    eps = Double.parseDouble(args[i+1]);
                    if(eps > 2 || eps < 0){
                        System.out.println("Epsilon values must be a double in range of [2,0>");
                        System.exit(1);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Epsilon values must be a double in range of [2,0>");
                    e.printStackTrace();
                }
            }
            if(args[i].equals("-path"))
                path = args[i+1];
            if(args[i].equals("-type")){
                if(args[i + 1].equals("mdp"))
                    type = ModelType.MDP;
                if(args[i + 1].equals("smm"))
                    type = ModelType.SMM;
                if(args[i + 1].equals("mc"))
                    type = ModelType.MC;
            }
            if(args[i].equals("-save"))
                saveLocation = args[i+1];
        }

        if(path==null) {
            System.out.println("Input file not specified.");
            System.exit(1);
        }
        return Arrays.asList(path, eps, type, saveLocation);
    }


    public static void addToFpta(FptaNode rootNode, FptaNode rootCopy, List<String> sample, ModelType modelType){
            FptaNode currNode = rootNode;
            FptaNode currCopy = rootCopy;

            int startingIndex = modelType == ModelType.MDP ? 1 : 0;
            int incrementSize = modelType == ModelType.MC ? 1 : 2;

            if(modelType != ModelType.SMM) {
                if (!sample.get(0).equals(currNode.output))
                    System.exit(1);
            }

            for (int i = startingIndex; i < sample.size() - 1; i+=incrementSize){
                String io = modelType != ModelType.MC ? sample.get(i) + '/' + sample.get(i+1) : sample.get(i);
                io = FptaNode.getFromStrCache(io);
                if(!currNode.children.containsKey(io)){
                    FptaNode node = new FptaNode(sample.get(i+startingIndex));
                    node.parentInputPair = new ParentInput(currNode, io);
                    currNode.children.put(io, node);

                    // Copy
                    FptaNode copy = new FptaNode(sample.get(i+startingIndex));
                    copy.parentInputPair = new ParentInput(currCopy, io);
                    currCopy.children.put(io, copy);
                }

                currNode.inputFrequency.put(io, currNode.inputFrequency.getOrDefault(io, 0) + 1);
                currNode = currNode.children.get(io);

                // Copy
                currCopy.inputFrequency.put(io, currCopy.inputFrequency.getOrDefault(io, 0) + 1);
                currCopy = currCopy.children.get(io);

            }
    }


    public static void saveModel(List<FptaNode> red, ModelType modelType, String saveLocation) {
        FileWriter fw;
        try {
            fw = new FileWriter(saveLocation + ".dot");
            fw.write("digraph g {\n");
            for (FptaNode r: red) {
                if(modelType != ModelType.SMM)
                    fw.write(r.stateId + " [shape=\"circle\",label=" + r.output + "];\n");
                else
                    fw.write(r.stateId + " [shape=\"circle\",label=" + r.stateId + "];\n");
            }
            for (FptaNode r: red)
                for (String io : r.children.keySet()) {
                    if(modelType == ModelType.MC){
                        fw.write(r.stateId + "->" + r.children.get(io).stateId + " [label=\"" +
                                r.childrenProbability.get(io) + "\"];\n");
                    }
                    if(modelType == ModelType.MDP){
                        List<String> inputAndOutput = Arrays.asList(io.split("/"));
                        fw.write(r.stateId + "->" + r.children.get(io).stateId + " [label=\"" +
                                inputAndOutput.get(0) + ":" + r.childrenProbability.get(io) + "\"];\n");
                    }
                    if(modelType == ModelType.SMM){
                        fw.write(r.stateId + "->" + r.children.get(io).stateId + " [label=\"" +
                                io + ":" + r.childrenProbability.get(io) + "\"];\n");
                    }
                }
            fw.write("__start0 [label=\"\" shape=\"none\"];\n");
            fw.write("__start0 -> q0  [label=\"\"];\n");
            fw.write("}\n");
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

public class Alergia {

    private final FptaNode t;
    private final FptaNode a;
    private final double epsilon;
    private final ModelType modelType;
    private final String saveLocation;

    public Alergia(String pathToFile, double eps, ModelType type, String saveFile){
        modelType = type;
        epsilon = eps;
        saveLocation = saveFile;

        double start = System.currentTimeMillis();
        List<FptaNode> ta = Parser.parseFile(pathToFile, modelType);
        double timeElapsed = System.currentTimeMillis() - start;
        System.out.println("IOFPTA construction time : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        t = ta.get(0);
        a = ta.get(1);
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

        fold(r, blueNode);
    }

    private void fold(FptaNode red, FptaNode blue) {
        for (String io : blue.children.keySet()){
            if(red.children.containsKey(io)){
                red.inputFrequency.put(io, red.inputFrequency.get(io) + blue.inputFrequency.get(io));

                fold(red.children.get(io), blue.children.get(io));
            }else{
                red.children.put(io, blue.children.get(io));
                red.inputFrequency.put(io, blue.inputFrequency.get(io));
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

    public static void main(String[] args) {
        List<Object> argValues = Parser.parseArgs(args);
        String path = (String) argValues.get(0);
        double eps = (Double) argValues.get(1);
        ModelType type = (ModelType) argValues.get(2);
        String saveLocation = (String) argValues.get(3);

        Alergia a = new Alergia(path, eps, type, saveLocation);

        System.exit(0);
    }
}
