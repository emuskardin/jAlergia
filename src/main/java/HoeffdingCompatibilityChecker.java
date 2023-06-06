import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Math.*;

/**
 * Compatibility Checker implementing Hoeffding statistical compatibility check as described
 * in https://link.springer.com/article/10.1007/s10994-016-5565-9;
 */
public class HoeffdingCompatibilityChecker implements CompatibilityChecker {
    double epsilon;
    public HoeffdingCompatibilityChecker(double eps){
        epsilon = eps;
    }

    @Override
    public boolean areStatesDifferent(FptaNode a, FptaNode b, ModelType modelType) {
        // No data available for any node
        if (a.inputFrequency.size() * b.inputFrequency.size() == 0)
            return false;

        if(modelType == ModelType.MC)
            return hoeffdingBound(a.inputFrequency, b.inputFrequency);

        // check hoeffding bound conditioned on inputs
        Map<String, Map<String, Integer>> aDict = getTwoStageDict(a.inputFrequency);
        Map<String, Map<String, Integer>> bDict = getTwoStageDict(b.inputFrequency);

        Set<String> inputIntersection = new HashSet<>(aDict.keySet());
        inputIntersection.retainAll(bDict.keySet());

        for (String key : inputIntersection) {
            if (hoeffdingBound(aDict.get(key), bDict.get(key)))
                return true;
        }
        return false;
    }

    public Map<String, Map<String, Integer>> getTwoStageDict(Map<String, Integer> inputDict) {
        Map<String, Map<String, Integer>> ret = new HashMap<>();
        for (Map.Entry<String, Integer> entry : inputDict.entrySet()) {
            String[] keys = entry.getKey().split("/");
            String inSym = keys[0];
            String outSym = keys[1];

            if (!ret.containsKey(inSym))
                ret.put(inSym, new HashMap<>());

            ret.get(inSym).put(outSym, entry.getValue());
        }
        return ret;
    }

    public boolean hoeffdingBound(Map<String, Integer> a, Map<String, Integer> b) {
        int n1 = a.values().stream().mapToInt(Integer::intValue).sum();
        int n2 = b.values().stream().mapToInt(Integer::intValue).sum();

        if (n1 * n2 == 0)
            return false;

        Set<String> outputUnion = new HashSet<>(a.keySet());
        outputUnion.addAll(b.keySet());

        for (String o : outputUnion) {
            double aFreq = a.getOrDefault(o, 0);
            double bFreq = b.getOrDefault(o, 0);

            if (abs(aFreq / n1 - bFreq / n2) > ((sqrt(1. / n1) + sqrt(1. / n2)) * sqrt(0.5 * log(2 / epsilon))))
                return true;
        }

        return false;
    }

}
