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
    double log_term;
    public HoeffdingCompatibilityChecker(double eps){
        epsilon = eps;
        log_term = sqrt(0.5 * log(2 / epsilon));
    }

    @Override
    public boolean areStatesDifferent(FptaNode a, FptaNode b, ModelType modelType) {
        // No data available for any node
        if (a.inputFrequency.size() * b.inputFrequency.size() == 0)
            return false;

        if(modelType == ModelType.MC)
            return hoeffdingBound(a.inputFrequency, b.inputFrequency);

        Set<String> inputIntersection = new HashSet<>(a.getInputs());
        inputIntersection.retainAll(b.getInputs());

        for (String key : inputIntersection) {
            if (hoeffdingBound(a.getOutputFrequencies(key), b.getOutputFrequencies(key)))
                return true;
        }
        return false;
    }

    public boolean hoeffdingBound(Map<String, Integer> a, Map<String, Integer> b) {
        double n1 = a.values().stream().mapToInt(Integer::intValue).sum();
        double n2 = b.values().stream().mapToInt(Integer::intValue).sum();

        if (n1 * n2 == 0)
            return false;

        double bound = (sqrt(1. / n1) + sqrt(1. / n2));
        Set<String> outputUnion = new HashSet<>(a.keySet());
        outputUnion.addAll(b.keySet());

        for (String o : outputUnion) {
            double aFreq = a.getOrDefault(o, 0);
            double bFreq = b.getOrDefault(o, 0);

            if (abs(aFreq / n1 - bFreq / n2) > (bound * log_term))
                return true;
        }

        return false;
    }

}
