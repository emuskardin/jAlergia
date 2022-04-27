import java.util.*;

/**
 * Markov Decision Process
 * Stochastic Mealy Machines
 * MC
 */
enum ModelType{
    MDP,
    SMM,
    MC,
}

/**
 * MEMORY makes Alergia work with the single tree, thus requiring half the memory size, but at the expanse of accuracy
 * ACCURACY uses both trees, mutable and immutable
 */
enum OptimizeFor{
    MEMORY,
    ACCURACY
}

/**
 * Class implementing Alergia passive learning algorithm as described in
 * "Learning deterministic probabilistic automata from a model checking perspective"
 * (https://link.springer.com/article/10.1007/s10994-016-5565-9).
 */
public class Alergia {

    private FptaNode mutableTree = null;
    private FptaNode immutableTree = null;
    private CompatibilityChecker compatibilityChecker;
    private ModelType modelType;
    private final String saveLocation;
    private OptimizeFor optimizeFor;

    /**
     * Default constructor. Model will be saved to "jAlergiaModel.dot".
     */
    public Alergia(){
        saveLocation = "jAlergiaModel";
    }

    /**
     * Default constructor. Model will be saved to "<saveFile>.dot".
     * @param saveFile path where models will be saved
     */
    public Alergia(String saveFile){
        saveLocation = saveFile;
    }

    /**
     * Runs the Alergia passive learning algorithm.
     * @param data input data
     * @param type model type
     * @param eps epsilon value for HoeffdingCompatibilityChecker
     * @param optim optimization method
     */
    public void runAlergia(List<List<String>> data, ModelType type, double eps, OptimizeFor optim){
        // automatic epsilon computation
        if(eps == -1){
            int denominator = 0;
            for(List<String> d : data)
                denominator += d.size()- 1;
            eps = 10. / denominator;
        }

        compatibilityChecker = new HoeffdingCompatibilityChecker(eps);
        modelType = type;
        optimizeFor = optim;

        constructFPTA(data);
        runMainAlergiaLoop();
    }

    /**
     * Runs the Alergia passive learning algorithm.
     * @param data input data
     * @param type model type
     * @param compChecker instance of CompatibilityChecker implementation
     * @param optim optimization method
     */
    public void runAlergia(List<List<String>> data, ModelType type, CompatibilityChecker compChecker, OptimizeFor optim){
        compatibilityChecker = compChecker;
        modelType = type;
        optimizeFor = optim;

        constructFPTA(data);
        runMainAlergiaLoop();
    }

    /**
     * Construct mutable and immutable trees. If optimization is set to MEMORY, blue tree is null.
     * @param data red and blue tree
     */
    private void constructFPTA(List<List<String>> data){
        double start = System.currentTimeMillis();
        List<FptaNode> ta = FptaNode.constructFPTA(data, modelType, this.optimizeFor);
        double timeElapsed = System.currentTimeMillis() - start;
        System.out.println("FPTA construction time   : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        data = null; // to ensure GC will collect it sooner than later

        mutableTree = ta.get(0);
        immutableTree = ta.get(1);
    }

    /**
     * Runs the main loop of the algorithm.
     */
    private void runMainAlergiaLoop() {
        double start = System.currentTimeMillis();

        List<FptaNode> red = new ArrayList<>();
        red.add(mutableTree);
        List<FptaNode> blue = new ArrayList<>(mutableTree.getSuccessors());

        while (!blue.isEmpty()){
            FptaNode lexMinBlue = getLexMin(blue);
            boolean merged = false;

            for (FptaNode r : red){
                if(compatibilityTest(getNodeFromT(r), getNodeFromT(lexMinBlue))){
                    merge(r, lexMinBlue);
                    merged = true;
                    break;
                }
            }

            if(!merged)
                insertInLexMinSort(red, lexMinBlue);

            blue.clear();

            for(FptaNode r:red){
                for (FptaNode s : r.getSuccessors()){
                    if(!red.contains(s))
                        blue.add(s);
                }
            }

        }

        normalize(red);
        Parser.saveModel(red, modelType, saveLocation);
        double timeElapsed = System.currentTimeMillis() - start;
        System.out.println("Alergia learning time    : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        System.out.println("Alergia learned " + red.size() + " state automaton.");
    }

    /**
     * Redirects lexMinBlue to r and folds their children
     * @param r red node
     * @param lexMinBlue blue node
     */
    private void merge(FptaNode r, FptaNode lexMinBlue) {
        FptaNode blueNode = getNodeFromT(lexMinBlue);
        List<String> prefixLeadingToState = new ArrayList<>(lexMinBlue.getPrefix());
        String lastIo = prefixLeadingToState.remove(prefixLeadingToState.size() - 1);

        FptaNode toUpdate = mutableTree;
        for (String p : prefixLeadingToState)
            toUpdate = toUpdate.children.get(p);

        toUpdate.children.put(lastIo, r);

        fold(r, lexMinBlue, blueNode);
    }

    /**
     * Folds blue subtree in red subtree.
     * @param red red node
     * @param blue blue node in red tree
     * @param blueTreeNode blue node from blue tree
     */
    private void fold(FptaNode red, FptaNode blue, FptaNode blueTreeNode) {
        for (String io : blue.children.keySet()){
            if(red.children.containsKey(io)){
                red.inputFrequency.put(io, red.inputFrequency.get(io) + blueTreeNode.inputFrequency.getOrDefault(io, 0));
                fold(red.children.get(io), blue.children.get(io), getNodeFromT(blue.children.get(io)));
            }else{
                red.children.put(io, blue.children.get(io));
                red.inputFrequency.put(io, blueTreeNode.inputFrequency.getOrDefault(io, 0));
            }
        }
    }

    /**
     * Check compatibility between nodes and their children.
     * @param a Fpta node
     * @param b Fpta node
     * @return True if a and b are compatible
     */
    private boolean compatibilityTest(FptaNode a, FptaNode b){
        if(modelType != ModelType.SMM && !a.output.equals(b.output))
            return false;

        if(a.children.values().isEmpty() || b.children.values().isEmpty())
            return true;

        if(!compatibilityChecker.checkDifferance(a,b))
            return false;

        Set<String> intersection  = new HashSet<>(a.children.keySet());
        intersection.retainAll(b.children.keySet());
        for (String child : intersection){
            if(!compatibilityTest(a.children.get(child), b.children.get(child)))
                return false;
        }

        return true;
    }

    /**
     * Insert blue in redList while preserving lexicographically minimal order.
     * @param redList list of automaton states/red nodes
     * @param blue blue node
     */
    private void insertInLexMinSort(List<FptaNode> redList, FptaNode blue){
        int index = 0;
        int bluePrefixSize = blue.getPrefix().size();
        for (FptaNode r : redList){
            if(r.getPrefix().size() < bluePrefixSize){
                index += 1;
            }else{
                break;
            }
        }
        redList.add(index, blue);
    }

    /**
     * Returns node with shortest prefix/lexicographically minimal node.
     * @param x list of Fpta nodes
     * @return lexicographically minimal node (node with shortest prefix)
     */
    private FptaNode getLexMin(List<FptaNode> x){
        FptaNode min = x.get(0);
        for (FptaNode node: x) {
            if(node.getPrefix().size() < min.getPrefix().size())
                min = node;
        }
        return min;
    }

    /**
     * Get node from immutable tree if optimizeFor is set to ACCURACY.
     * @param redNode node in a mutable tree
     * @return matching node in immutable tree if optimization is set to accuracy, or red node if memory optimization is used
     */
    private FptaNode getNodeFromT(FptaNode redNode){
        if(optimizeFor == OptimizeFor.MEMORY)
            return redNode;

        FptaNode blueNode = immutableTree;
        for(String p : redNode.getPrefix())
            blueNode = blueNode.children.get(p);
        return blueNode;
    }


    /**
     * Normalizes probabilities of final states, that it assigns probabilities to transitions for each state.
     * @param red list of states of learned automaton
     */
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

    /**
     * Simple example demonstrating how to use jAlergia.
     */
    public static void usageExample(){
        String path = "sampleFiles/mdpData6.txt";
        double eps = 0.005;
        ModelType type = ModelType.MDP;
        String saveLocation = "jAlergiaModel";
        OptimizeFor optimizeFor = OptimizeFor.ACCURACY;

        List<List<String>> data = Parser.parseFile(path);
        Alergia a = new Alergia(saveLocation);
        a.runAlergia(data, type, eps, optimizeFor);

        System.exit(0);
    }

    /**
     * @param args argument list defined for command line use. For more details run alergia.jar with -h option.
     */
    public static void main(String[] args) {
        List<Object> argValues = Parser.parseArgs(args);

        String path = (String) argValues.get(0);
        double eps = (Double) argValues.get(1);
        ModelType type = (ModelType) argValues.get(2);
        String saveLocation = (String) argValues.get(3);
        OptimizeFor optimizeFor = (OptimizeFor) argValues.get(4);

        List<List<String>> data = Parser.parseFile(path);
        Alergia a = new Alergia(saveLocation);
        a.runAlergia(data, type, eps, optimizeFor);
        System.exit(0);
    }
}
