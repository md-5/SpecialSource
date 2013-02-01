package net.md_5.specialsource;

import java.util.ArrayList;
import java.util.List;

/**
 * Lookup inheritance information from multiple sources, in order
 */
public class InheritanceProviders implements IInheritanceProvider {
    private List<IInheritanceProvider> inheritanceProviders;

    public InheritanceProviders() {
        inheritanceProviders = new ArrayList<IInheritanceProvider>();
    }

    public void add(IInheritanceProvider inheritanceProvider) {
        inheritanceProviders.add(inheritanceProvider);
    }

    @Override
    public List<String> getParents(String owner) {
        for (IInheritanceProvider inheritanceProvider : inheritanceProviders) {
            // ask each provider for inheritance information on the class, until one responds
            List<String> parents = inheritanceProvider.getParents(owner);

            if (parents != null) {
                return parents;
            }
        }

        return null;
    }
}
