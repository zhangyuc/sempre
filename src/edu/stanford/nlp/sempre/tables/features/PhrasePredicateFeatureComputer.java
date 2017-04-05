package edu.stanford.nlp.sempre.tables.features;

import java.util.*;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.tables.features.PredicateInfo.PredicateType;
import fig.basic.*;

/**
 * Extract features based on (phrase, predicate) pairs.
 *
 * - |phrase| is an n-gram from the utterance (usually n = 1)
 * - |predicate| is a predicate (LispTree leaf) from the formula
 *   Example: fb:cell_name.barack_obama, fb:row.row.name, argmax
 *
 * Properties of phrases: POS tags, length, word shapes, ...
 * Properties of predicates: category (entity / binary / keyword), ...
 * Properties of alignment: exact match, prefix match, suffix match, string contains, ...
 *
 * @author ppasupat
 */
public class PhrasePredicateFeatureComputer implements FeatureComputer {
  public static class Options {
    @Option(gloss = "Verbosity") public int verbose = 0;
    @Option(gloss = "Define features on partial derivations as well")
    public boolean defineOnPartialDerivs = true;
    @Option(gloss = "Also define features on prefix and suffix matches")
    public boolean usePrefixSuffixMatch = true;
    @Option(gloss = "Also define features with POS tags")
    public boolean usePosFeatures = true;
    @Option(gloss = "Define unlexicalized phrase-predicate features")
    public boolean unlexicalizedPhrasePredicate = true;
    @Option(gloss = "Define lexicalized phrase-predicate features")
    public boolean lexicalizedPhrasePredicate = true;
    @Option(gloss = "Maximum ngram length for lexicalize all pair features")
    public int maxNforLexicalizeAllPairs = Integer.MAX_VALUE;
  }
  public static Options opts = new Options();

  public final int maxNforLexicalizeAllPairs;

  public PhrasePredicateFeatureComputer() {
    maxNforLexicalizeAllPairs = Math.min(opts.maxNforLexicalizeAllPairs, PhraseInfo.opts.maxPhraseLength);
  }

  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!(FeatureExtractor.containsDomain("phrase-predicate")
          || FeatureExtractor.containsDomain("missing-predicate")
          || FeatureExtractor.containsDomain("phrase-formula"))) return;
    // Only compute features at the root, except when the partial option is set.
    if (!opts.defineOnPartialDerivs && !deriv.isRoot(ex.numTokens())) return;
    List<PhraseInfo> phraseInfos = PhraseInfo.getPhraseInfos(ex);
    List<PredicateInfo> predicateInfos = PredicateInfo.getPredicateInfos(ex, deriv);
    if (opts.verbose >= 2) {
      LogInfo.logs("Example: %s", ex.utterance);
      LogInfo.logs("Phrases: %s", phraseInfos);
      LogInfo.logs("Derivation: %s", deriv);
      LogInfo.logs("Predicates: %s", predicateInfos);
    }
    if (FeatureExtractor.containsDomain("phrase-predicate")) {
      if (opts.defineOnPartialDerivs) {
        deriv.getTempState().put("p-p", new ArrayList<>(predicateInfos));
        // Subtract predicates from children
        Map<PredicateInfo, Integer> predicateInfoCounts = new HashMap<>();
        for (PredicateInfo predicateInfo : predicateInfos)
          MapUtils.incr(predicateInfoCounts, predicateInfo);
        if (deriv.children != null) {
          for (Derivation child : deriv.children) {
            @SuppressWarnings("unchecked")
            List<PredicateInfo> childPredicateInfos = (List<PredicateInfo>) child.getTempState().get("p-p");
            for (PredicateInfo predicateInfo : childPredicateInfos)
              MapUtils.incr(predicateInfoCounts, predicateInfo, -1);
          }
        }
        for (PhraseInfo phraseInfo : phraseInfos) {
          for (Map.Entry<PredicateInfo, Integer> entry : predicateInfoCounts.entrySet()) {
            if (entry.getValue() != 0)
              extractMatch(ex, deriv, phraseInfo, entry.getKey(), entry.getValue());
          }
        }
      } else {
        for (PhraseInfo phraseInfo : phraseInfos) {
          for (PredicateInfo predicateInfo : predicateInfos) {
            extractMatch(ex, deriv, phraseInfo, predicateInfo, 1);
          }
        }
      }
    }
    if (FeatureExtractor.containsDomain("missing-predicate")) {
      extractMissing(ex, deriv, phraseInfos, predicateInfos);
    }
    if (FeatureExtractor.containsDomain("phrase-formula")) {
      extractPhraseFormula(ex, deriv, phraseInfos);
    }
  }

  // ============================================================
  // Matching
  // ============================================================

  private void extractMatch(Example ex, Derivation deriv, PhraseInfo phraseInfo, PredicateInfo predicateInfo, double factor) {
    if (predicateInfo.originalString != null) {
      if (!PhraseInfo.opts.usePhraseLemmaOnly) {
        extractMatch(ex, deriv, phraseInfo, phraseInfo.text, "(o)",
            predicateInfo, predicateInfo.originalString, "(o)", factor);
      }
      extractMatch(ex, deriv, phraseInfo, phraseInfo.lemmaText, "",
          predicateInfo, predicateInfo.originalString, "(o)", factor);
    } else {
      if (!PhraseInfo.opts.usePhraseLemmaOnly) {
        extractMatch(ex, deriv, phraseInfo, phraseInfo.text, "(o)",
            predicateInfo, predicateInfo.predicate, "(i)", factor);
      }
      extractMatch(ex, deriv, phraseInfo, phraseInfo.lemmaText, "",
          predicateInfo, predicateInfo.predicate, "(i)", factor);
    }
  }

  private void extractMatch(Example ex, Derivation deriv,
      PhraseInfo phraseInfo, String phraseString, String phraseType,
      PredicateInfo predicateInfo, String predicateString, String predicateType, double factor) {
    if (opts.unlexicalizedPhrasePredicate) {
      if (phraseString.equals(predicateString)) {
        defineFeatures(ex, deriv, phraseInfo, predicateInfo, phraseType + "=" + predicateType,
            phraseString, predicateString, factor);
      } else if (opts.usePrefixSuffixMatch) {
        if (predicateString.startsWith(phraseString)) {
          defineFeatures(ex, deriv, phraseInfo, predicateInfo, "*_" + phraseType + "=" + predicateType,
              phraseString, predicateString, factor);
        }
        if (predicateString.endsWith(phraseString)) {
          defineFeatures(ex, deriv, phraseInfo, predicateInfo, "_*" + phraseType + "=" + predicateType,
              phraseString, predicateString, factor);
        }
        if (phraseString.startsWith(predicateString)) {
          defineFeatures(ex, deriv, phraseInfo, predicateInfo, phraseType + "=_*" + predicateType,
              phraseString, predicateString, factor);
        }
        if (phraseString.endsWith(predicateString)) {
          defineFeatures(ex, deriv, phraseInfo, predicateInfo, phraseType + "=*_" + predicateType,
              phraseString, predicateString, factor);
        }
      }
    }
    if (opts.lexicalizedPhrasePredicate && phraseInfo.end - phraseInfo.start <= maxNforLexicalizeAllPairs) {
      deriv.addFeature("p-p",
          phraseType + phraseString + ";" + predicateType + predicateString, factor);
    }
  }

  private void defineFeatures(Example ex, Derivation deriv, PhraseInfo phraseInfo, PredicateInfo predicateInfo,
      String featurePrefix, String phraseString, String predicateString, double factor) {
    defineFeatures(ex, deriv, phraseInfo, predicateInfo, featurePrefix, factor);
    if (opts.usePosFeatures)
      defineFeatures(ex, deriv, phraseInfo, predicateInfo,
          featurePrefix + "," + phraseInfo.canonicalPosSeq, factor);
  }

  private void defineFeatures(Example ex, Derivation deriv, PhraseInfo phraseInfo, PredicateInfo predicateInfo,
      String featurePrefix, double factor) {
    if (opts.verbose >= 2) LogInfo.logs("defineFeatures: %s %s %s %s",
        featurePrefix, phraseInfo, predicateInfo, predicateInfo.type);
    deriv.addFeature("p-p", featurePrefix, factor);
    deriv.addFeature("p-p", featurePrefix + "," + predicateInfo.type, factor);
  }

  // ============================================================
  // Missing predicate features
  // ============================================================

  private void extractMissing(Example ex, Derivation deriv, List<PhraseInfo> phraseInfos, List<PredicateInfo> predicateInfos) {
    // Only makes sense at the root
    if (!deriv.isRoot(ex.numTokens())) return;
    // Get the list of all relevant predicates
    Set<String> relevantPredicates = new HashSet<>();
    for (PredicateInfo predicateInfo : predicateInfos) {
      if (predicateInfo.type == PredicateType.BINARY || predicateInfo.type == PredicateType.ENTITY) {
        String predicate = predicateInfo.predicate;
        if (predicate.charAt(0) == '!') predicate = predicate.substring(1);
        relevantPredicates.add(predicate);
      }
    }
    // See which predicates are missing!
    Set<String> missingPredicates = new HashSet<>();
    for (PhraseInfo phraseInfo : phraseInfos) {
      if (phraseInfo.fuzzyMatchedPredicates == null) continue;
      for (String predicate : phraseInfo.fuzzyMatchedPredicates) {
        if (!relevantPredicates.contains(predicate)) {
          missingPredicates.add(predicate);
          missingPredicates.add("type=" + PredicateInfo.inferType(predicate));
        }
      }
    }
    if (opts.verbose >= 2) {
      LogInfo.logs("have %s", relevantPredicates);
      LogInfo.logs("missing %s", missingPredicates);
    }
    for (String missing : missingPredicates) {
      deriv.addFeature("m-p", missing);
    }
  }

  // ============================================================
  // Phrase - Formula features
  // ============================================================

  private void extractPhraseFormula(Example ex, Derivation deriv, List<PhraseInfo> phraseInfos) {
    if (deriv.formula instanceof LambdaFormula) return;
    // Get rough formula
    LispTree tree = PredicateInfo.normalizeFormula(ex, deriv);
    if (opts.verbose >= 2) {
      LogInfo.logs("original formula %s", deriv.formula);
      LogInfo.logs("normalized formula %s", tree);
    }
    deriv.addFeature("p-f", "" + tree);
    for (PhraseInfo phraseInfo : phraseInfos) {
      if (!PhraseInfo.opts.usePhraseLemmaOnly) {
        deriv.addFeature("p-f", phraseInfo.text + "(o);" + tree);
      }
      deriv.addFeature("p-f", phraseInfo.lemmaText + ";" + tree);
    }
  }

}
