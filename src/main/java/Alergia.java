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
 * Pair class used for iterative folding and compatibility test
 */
class Pair<T, U> {
    public final T first;
    public final U second;

    public Pair(T first, U second) {
        this.first = first;
        this.second = second;
    }

}

/**
 * Class implementing Alergia passive learning algorithm as described in
 * "Learning deterministic probabilistic automata from a model checking perspective"
 * (https://link.springer.com/article/10.1007/s10994-016-5565-9).
 */
public class Alergia {

    private FptaNode mutableTree = null;
    private CompatibilityChecker compatibilityChecker;
    private ModelType modelType;
    private final String saveLocation;

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
     */
    public void runAlergia(List<List<String>> data, ModelType type, double eps){
        // automatic epsilon computation
        if(eps == -1){
            int denominator = 0;
            for(List<String> d : data)
                denominator += d.size()- 1;
            eps = 10. / denominator;
        }

        compatibilityChecker = new HoeffdingCompatibilityChecker(eps);
        modelType = type;

        constructFPTA(data);
        runMainAlergiaLoop();
    }

    /**
     * Runs the Alergia passive learning algorithm.
     * @param data input data
     * @param type model type
     * @param compChecker instance of CompatibilityChecker implementation
     */
    public void runAlergia(List<List<String>> data, ModelType type, CompatibilityChecker compChecker){
        compatibilityChecker = compChecker;
        modelType = type;

        constructFPTA(data);
        runMainAlergiaLoop();
    }

    /**
     * Construct mutable and immutable trees. If optimization is set to MEMORY, blue tree is null.
     * @param data red and blue tree
     */
    private void constructFPTA(List<List<String>> data){
        double start = System.currentTimeMillis();
        mutableTree = FptaNode.constructFPTA(data, modelType);
        double timeElapsed = System.currentTimeMillis() - start;
        System.out.println("FPTA construction time   : " + String.format("%.2f", timeElapsed / 1000) + " seconds.");
        data = null; // to ensure GC will collect it sooner than later

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
                if(compatibilityTest(r, lexMinBlue)){
                    merge(r, lexMinBlue);
                    merged = true;
                    break;
                }
            }

            if(!merged)
                insertInLexMinSort(red, lexMinBlue);

            List<Integer> prefixLength = new ArrayList<>();
            for (FptaNode node : red)
                prefixLength.add(node.prefix.size());

            assert prefixLength.stream().allMatch(i -> i.equals(prefixLength.get(0)) ||
                    i >= prefixLength.get(prefixLength.indexOf(i) - 1)) : "The list is not sorted";

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
        List<String> prefixLeadingToState = new ArrayList<>(lexMinBlue.prefix);
        String lastIo = prefixLeadingToState.remove(prefixLeadingToState.size() - 1);

        FptaNode toUpdate = mutableTree;
        for (String p : prefixLeadingToState)
            toUpdate = toUpdate.children.get(p);

        toUpdate.children.put(lastIo, r);

        fold(r, lexMinBlue);
    }

    /**
     * Folds blue subtree in red subtree.
     * @param redSubtreeRoot red node
     * @param blueSubtreeRoot blue node in red tree
     */
    private void fold(FptaNode redSubtreeRoot, FptaNode blueSubtreeRoot) {
        Queue<Pair<FptaNode, FptaNode>> queue = new LinkedList<>();
        queue.add(new Pair<>(redSubtreeRoot, blueSubtreeRoot));

        while (!queue.isEmpty()) {
            Pair<FptaNode, FptaNode> fptaPair = queue.poll();
            FptaNode red = fptaPair.first;
            FptaNode blue = fptaPair.second;

            for (String io : blue.children.keySet()){
                if (red.children.containsKey(io)) {
                    red.inputFrequency.put(io, red.inputFrequency.get(io) + blue.inputFrequency.get(io));
                    queue.add(new Pair<>(red.children.get(io), blue.children.get(io)));
                } else {
                    red.children.put(io, blue.children.get(io));
                    red.inputFrequency.put(io, blue.inputFrequency.get(io));
                }
            }
        }

    }

    /**
     * Check compatibility between nodes and their children.
     * @param redSubtree Fpta node
     * @param blueSubtree Fpta node
     * @return True if a and b are compatible
     */
    private boolean compatibilityTest(FptaNode redSubtree, FptaNode blueSubtree){
        Queue<Pair<FptaNode, FptaNode>> queue = new LinkedList<>();
        queue.add(new Pair<>(redSubtree, blueSubtree));

        while (!queue.isEmpty()) {
            Pair<FptaNode, FptaNode> nodesUnderTest = queue.poll();
            FptaNode a = nodesUnderTest.first;
            FptaNode b = nodesUnderTest.second;

            if (modelType != ModelType.SMM && !a.output.equals(b.output))
                return false;

            if (a.immutableChildren == null || b.immutableChildren == null)
                continue;

            if (compatibilityChecker.areStatesDifferent(a, b, modelType))
                return false;

            Set<String> intersection = new HashSet<>(a.immutableChildren.keySet());
            intersection.retainAll(b.immutableChildren.keySet());
            for (String child : intersection)
                queue.add(new Pair<>(a.immutableChildren.get(child), b.immutableChildren.get(child)));
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
        for (FptaNode r : redList){
            if(r.compareTo(blue) < 0) {
                index += 1;
            }
            else{
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
            if(node.compareTo(min) < 0)
                min = node;
        }
        return min;
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
                for (String io : r.inputFrequency.keySet()) {
                    List<String> inputAndOutput = Arrays.asList(io.split("/"));
                    r.childrenProbability.put(io, (double) (r.inputFrequency.get(io) /
                            r.getInputFrequency(inputAndOutput.get(0), false)));
                }
            }
        }
    }

    /**
     * Simple example demonstrating how to use jAlergia.
     */
    public static void usageExample(){
        String path = "sampleFiles/smmData_size_10.txt";
        double eps = 0.05;
        ModelType type = ModelType.SMM;
        String saveLocation = "jAlergiaModel";

        List<List<String>> data = Parser.parseFile(path);
        Alergia a = new Alergia(saveLocation);
        a.runAlergia(data, type, eps);

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

        List<List<String>> data = Parser.parseFile(path);
        Alergia a = new Alergia(saveLocation);
        a.runAlergia(data, type, eps);
        System.exit(0);
    }
}
