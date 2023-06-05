/**
 * CompatibilityChecker interface.
 */
public interface CompatibilityChecker {
    /**
     * Checked statistical compatibility between two nodes.
     * @param a Fpta node
     * @param b Fpta node
     * @return true if nodes are compatible
     */
    public boolean areStatesDifferent(FptaNode a, FptaNode b);
}

