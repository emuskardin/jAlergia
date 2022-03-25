import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;


class Parser{

    static String helpDisplayMessage = "Welcome to jAlergia, a minimal Alergia implementation in Java.\n" +
            "To use jAlergia, you need to have a file with input(output) data following the syntax found at\n" +
            "https://github.com/emuskardin/jAlergia and https://github.com/DES-Lab/AALpy/wiki/Passive-Learning-of-Stochastic-Automata\n" +
            "If heap is overflown during IOFPTA construction, consider extending it with -Xmx12G.\n\n" +
            "Mandatory arguments\n" +
            "\t-input <pathToInputFile> - file needs to conform to above mentioned syntax\n" +
            "\t-type <modelType> - either mdp, smm, or mc; If you want to learn Markov Decision Process, Stochastic Mealy Machine, or Markov Chain\n" +
            "Optional arguments\n" +
            "\t-eps <doubleVal> - value of the epsilon constant in Hoeffding compatibility check. Default: 0.005\n" +
            "\t-save <saveFileName> - file in which learned model will be saved. Default: jAlergiaModel\n" +
            "\t-optim <optimType> - either mem or acc, to optimize for memory usage or learned model accuracy.";

    public static List<Object> parseArgs(String[] args){
        double eps = 0.005;
        ModelType type = null;
        String path = null;
        String saveLocation = "jAlergiaModel";
        OptimizeFor optimizeFor = OptimizeFor.ACCURACY;

        HashSet<String> argNames = new HashSet<>(Arrays.asList("-eps", "-input", "-type", "-save", "-optim"));
        if(args.length == 0 || args[0].equals("-help") || args[0].equals("-h") || args[0].equals("--help")){
            System.out.println(helpDisplayMessage);
            System.exit(0);
        }
        for (int i = 0; i < args.length - 1; i+=2) {
            if(!argNames.contains(args[i])) {
                System.out.println("Unrecognized option '" + args[i] + "'.\nRun Use -help to see all arguments.");
                System.exit(1);
            }

            if(args[i].equals("-eps")){
                try {
                    eps = Double.parseDouble(args[i+1]);
                    if(eps > 2 || eps <= 0){
                        System.out.println("Epsilon values must be a double in range of [2,0>");
                        System.exit(1);
                    }
                } catch (NumberFormatException e) {
                    System.out.println("Epsilon values must be a double in range of [2,0>");
                    e.printStackTrace();
                }
            }
            if(args[i].equals("-input"))
                path = args[i+1];
            if(args[i].equals("-type")){
                switch (args[i + 1]) {
                    case "mdp":
                        type = ModelType.MDP;
                        break;
                    case "smm":
                        type = ModelType.SMM;
                        break;
                    case "mc":
                        type = ModelType.MC;
                        break;
                    default:
                        System.out.println("Invalid -type option. Make sure it is either mdp, smm, or mc.");
                        System.exit(1);
                }
            }
            if(args[i].equals("-save"))
                saveLocation = args[i+1];
            if(args[i].equals("-optim")){
                if(args[i+1].equals("mem"))
                    optimizeFor = OptimizeFor.MEMORY;
                else if (args[i+1].equals("acc"))
                    optimizeFor = OptimizeFor.ACCURACY;
                else {
                    System.out.println("Invalid optimize for option, set to accuracy optimization.");
                    optimizeFor = OptimizeFor.ACCURACY;
                }
            }
        }

        if(path==null) {
            System.out.println("Input file not specified. For more details use -h option.");
            System.exit(1);
        }
        if(type==null){
            System.out.println("Automaton type not specified. For more details use -h option.");
            System.exit(1);
        }
        return Arrays.asList(path, eps, type, saveLocation, optimizeFor);
    }

    public static List<List<String>> parseFile(String path){
        List<List<String>> data = new ArrayList<>();

        try {
            for (String line : Files.readAllLines(Paths.get(path))) {
                if(line.isEmpty())
                    continue;
                data.add(Arrays.asList(line.split(",")));
            }
        } catch (IOException e) {
            System.out.println("jAlergia Error: Input file could not be opened.");
            e.printStackTrace();
            System.exit(1);
        }
        return data;
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