import java.util.*;

/**
 * Helper class to keep track of parent nodes. Used for computing prefixes of nodes.
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

    public final String output;
    public ParentInputPair parentInputPair;

    // mutable
    public Map<String, FptaNode> children;
    public Map<String, Integer> inputFrequency;
    // immutable
    public Map<String, FptaNode> immutableChildren;
    public Map<String, Integer> immutableInputFrequency;

    // for writing to file
    public String stateId;
    public Map<String, Double> childrenProbability;

    public FptaNode(String o){
        this.output = o;
        this.children = new TreeMap<>();
        this.inputFrequency = new TreeMap<>();

        this.immutableChildren = new TreeMap<>();
        this.immutableInputFrequency = new TreeMap<>();
    }


    public Set<String> getInputs(boolean immutable){
        Map<String, Integer> frequencyMap = immutable ? immutableInputFrequency : inputFrequency;
        Set<String> inputs = new HashSet<>();
        for (String key : frequencyMap.keySet()) {
            String input = Arrays.asList(key.split("/")).get(0);
            inputs.add(input);
        }
        return inputs;
    }

    public int getInputFrequency(String targetInput, boolean immutable) {
        Map<String, Integer> frequencyMap = immutable ? immutableInputFrequency : inputFrequency;
        int frequency = 0;
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            String input = Arrays.asList(entry.getKey().split("/")).get(0);
            int freq = entry.getValue();
            if (input.equals(targetInput))
                frequency += freq;
        }
        return frequency;
    }

    public Map<String, Integer> getOutputFrequencies(String targetInput, boolean immutable) {
        Map<String, Integer> frequencyMap = immutable ? immutableInputFrequency : inputFrequency;
        Map<String, Integer> outputFrequencies = new HashMap<>();
        for (Map.Entry<String, Integer> entry : frequencyMap.entrySet()) {
            List<String> inputOutputPair = Arrays.asList(entry.getKey().split("/"));
            String input = inputOutputPair.get(0);
            String output = inputOutputPair.get(1);
            if (input.equals(targetInput))
                outputFrequencies.put(output, entry.getValue());
        }
        return outputFrequencies;
    }

    public int compareTo(FptaNode other) {
        // First, compare the lengths of prefix lists
        int lengthComparison = Integer.compare(this.getPrefix().size(), other.getPrefix().size());
        if (lengthComparison != 0) {
            return lengthComparison;
        }

        // If lengths are equal, compare the strings at each index lexicographically
        for (int i = 0; i < this.getPrefix().size(); i++) {
            int strComparison = this.getPrefix().get(i).compareTo(other.getPrefix().get(i));
            if (strComparison != 0)
                return strComparison;
        }

        // If both lengths and strings are equal, the objects are considered equal
        return 0;
    }

    /**
     * @return path from root node to current node
     */
    public List<String> getPrefix(){
        Deque<String> prefix = new LinkedList<>();
        FptaNode p = this;
        while (p.parentInputPair != null) {
            prefix.addFirst(p.parentInputPair.inputOutput);
            p = p.parentInputPair.parent;
        }
        return new ArrayList<>(prefix);
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
     * @return successor of the node
     */
    public Collection<? extends FptaNode> getSuccessors() {
        return this.children.values();
    }

    /**
     * Construct mutable and immutable trees.
     * @param data list of lists of strings conforming to syntax defined at https://github.com/emuskardin/jAlergia
     * @param modelType mdp, smm, or mc
     * @return mutable and immutable trees (second tree is set to null in case of memory optimization)
     */
    public static FptaNode constructFPTA(List<List<String>> data, ModelType modelType){

        FptaNode rootNode = new FptaNode(FptaNode.getFromStrCache(data.get(0).get(0)));
        rootNode.parentInputPair = null;

        int startingIndex = modelType != ModelType.SMM ? 1 : 0;
        int incrementSize = modelType == ModelType.MC ? 1 : 2;

        while (!data.isEmpty()){
            List<String> sample = data.remove(0);
            FptaNode currNode = rootNode;

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
                    String output = FptaNode.getFromStrCache(sample.get(modelType == ModelType.MC ? i : i + startingIndex));

                    FptaNode node = new FptaNode(output);
                    node.parentInputPair = new ParentInputPair(currNode, io);

                    currNode.children.put(io, node);
                    currNode.inputFrequency.put(io, 0);

                    currNode.immutableChildren.put(io, node);
                    currNode.immutableInputFrequency.put(io, 0);

                }

                currNode.inputFrequency.put(io, currNode.inputFrequency.get(io) + 1);
                currNode.immutableInputFrequency.put(io, currNode.immutableInputFrequency.get(io) + 1);

                currNode = currNode.children.get(io);

            }
        }

        return rootNode;
    }

}



