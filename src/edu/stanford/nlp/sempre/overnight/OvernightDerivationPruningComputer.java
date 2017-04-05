package edu.stanford.nlp.sempre.overnight;

import java.util.*;
import java.util.regex.Pattern;

import edu.stanford.nlp.sempre.*;
import fig.basic.Option;
import fig.basic.MapUtils;

/**
 * Hard-coded hacks for pruning derivations in floating parser for overnight domains.
 *
 */

public class OvernightDerivationPruningComputer extends DerivationPruningComputer {
  public static class Options {
    @Option (gloss = "Whether filter derivations using hard constraints")
    public boolean applyHardConstraints = false;
  }
  public static Options opts = new Options();

  
  public OvernightDerivationPruningComputer(DerivationPruner pruner) {
    super(pruner);
  }

  @Override
  public boolean isPruned(Example ex, Derivation deriv) {
    if (opts.applyHardConstraints && violateHardConstraints(deriv)) return true;
    return false;
  }

  // Check a few hard constraints on each derivation
  private static boolean violateHardConstraints(Derivation deriv) {
    if (deriv.value != null) {
      if (deriv.value instanceof ErrorValue) return true;
      if (deriv.value instanceof StringValue) { //empty denotation
        if (((StringValue) deriv.value).value.equals("[]")) return true;
      }
      if (deriv.value instanceof ListValue) {
        List<Value> values = ((ListValue) deriv.value).values;
        // empty lists
        if (values.size() == 0) return true;
        // NaN
        if (values.size() == 1 && values.get(0) instanceof NumberValue) {
          if (Double.isNaN(((NumberValue) values.get(0)).value)) return true;
        }
        // If we are supposed to get a number but we get a string (some sparql weirdness)
        if (deriv.type.equals(SemType.numberType) &&
                values.size() == 1 &&
                !(values.get(0) instanceof NumberValue)) return true;
      }
    }
    return false;
  }

}
