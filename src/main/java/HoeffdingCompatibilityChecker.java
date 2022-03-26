import java.util.HashSet;
import java.util.Set;

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
    public boolean checkDifferance(FptaNode a, FptaNode b) {
        int n1 = a.inputFrequency.values().stream().mapToInt(Integer::intValue).sum();
        int n2 = b.inputFrequency.values().stream().mapToInt(Integer::intValue).sum();

        if (n1 > 0 && n2 > 0) {
            Set<String> aChildren = a.children.keySet();
            Set<String> bChildren = b.children.keySet();

            Set<String> union = new HashSet<>(aChildren);
            union.addAll(bChildren);
            for (String o : union) {
                double aFreq = a.inputFrequency.getOrDefault(o, 0);
                double bFreq = b.inputFrequency.getOrDefault(o, 0);

                if (abs(aFreq / n1 - bFreq / n2) > ((sqrt(1. / n1) + sqrt(1. / n2)) * sqrt(0.5 * log(2 / epsilon))))
                    return false;
            }
        }
        return true;
    }
}
