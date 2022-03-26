import java.util.*;

/**
 * Heper class to keep track of parent nodes. Used for computing prefixes of nodes.
 */
class ParentInputPair {
    FptaNode parent;
    String inputOutput;
    public ParentInputPair(FptaNode p, String io){
        parent = p;
        inputOutput = io;
    }
}

/**
 * Frequency prefix tree acceptor (FPTA) node class.
 * Each node hold references to its children and other needed information.
 */
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

    /**
     * String cache to ensure that same references are used for same strings.
     * @param str string
     * @return string instance from String cache
     */
    static String getFromStrCache(String str){
        FptaNode.stringCache.putIfAbsent(str, str);
        return FptaNode.stringCache.get(str);
    }

    /**
     * @return path from root node to current node
     */
    public List<String> getPrefix(){
        List<String> prefix = new ArrayList<>();
        FptaNode p = this;
        while (p.parentInputPair.parent != null) {
            prefix.add(0, p.parentInputPair.inputOutput);
            p = p.parentInputPair.parent;
        }
        return prefix;
    }

    /**
     * @return successor of the node
     */
    public Collection<? extends FptaNode> getSuccessors() {
        return this.children.values();
    }

    /**
     * Construct mutable and immutable trees.
     * @param data list of lists of strings conforming to syntax defined at https://github.com/emuskardin/jAlergia
     * @param modelType mdp, smm, or mc
     * @param optimizeFor if optimize for memory is used, only mutable tree will be created
     * @return mutable and immutable trees (second tree is set to null in case of memory optimization)
     */
    public static List<FptaNode> constructFPTA(List<List<String>> data, ModelType modelType, OptimizeFor optimizeFor){
        FptaNode rootNode = new FptaNode(FptaNode.getFromStrCache(data.get(0).get(0)));
        rootNode.parentInputPair = new ParentInputPair(null, null);

        FptaNode rootCopy = null;

        if(optimizeFor == OptimizeFor.ACCURACY) {
            rootCopy = new FptaNode(FptaNode.getFromStrCache(data.get(0).get(0)));
            rootCopy.parentInputPair = new ParentInputPair(null, null);
        }

        int startingIndex = modelType == ModelType.MDP ? 1 : 0;
        int incrementSize = modelType == ModelType.MC ? 1 : 2;

        for (List<String> sample : data) {
            FptaNode currNode = rootNode;
            FptaNode currCopy = rootCopy;

            if (modelType != ModelType.SMM) {
                if (!sample.get(0).equals(rootNode.output)) {
                    System.out.println("All initial outputs are not the same.\n" +
                            "Make data conform to the syntax defines at https://github.com/emuskardin/jAlergia\n" +
                            "Alternatively add a dummy initial output.\n" +
                            "Terminating Alergia.");
                    System.exit(1);
                }
            }

            for (int i = startingIndex; i < sample.size() - 1; i += incrementSize) {
                String io = modelType != ModelType.MC ? sample.get(i) + '/' + sample.get(i + 1) : sample.get(i);
                io = FptaNode.getFromStrCache(io);
                if (!currNode.children.containsKey(io)) {
                    String output = FptaNode.getFromStrCache(sample.get(i + startingIndex));
                    FptaNode node = new FptaNode(output);
                    node.parentInputPair = new ParentInputPair(currNode, io);
                    currNode.children.put(io, node);
                    currNode.inputFrequency.put(io, 0);

                    // Copy
                    if (optimizeFor == OptimizeFor.ACCURACY) {
                        FptaNode copy = new FptaNode(output);
                        copy.parentInputPair = new ParentInputPair(currCopy, io);
                        currCopy.children.put(io, copy);
                        currCopy.inputFrequency.put(io, 0);
                    }
                }

                currNode.inputFrequency.put(io, currNode.inputFrequency.get(io) + 1);
                currNode = currNode.children.get(io);

                // Copy
                if (optimizeFor == OptimizeFor.ACCURACY) {
                    currCopy.inputFrequency.put(io, currCopy.inputFrequency.get(io) + 1);
                    currCopy = currCopy.children.get(io);
                }
            }
        }

        return Arrays.asList(rootNode, rootCopy);
    }

}



