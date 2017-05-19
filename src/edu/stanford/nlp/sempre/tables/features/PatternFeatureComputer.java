package edu.stanford.nlp.sempre.tables.features;
import java.util.List;

import edu.stanford.nlp.sempre.*;
import edu.stanford.nlp.sempre.cprune.*;

public class PatternFeatureComputer implements FeatureComputer {
  @Override
  public void extractLocal(Example ex, Derivation deriv) {
    if (!(FeatureExtractor.containsDomain("phrase-pattern") || FeatureExtractor.containsDomain("pattern-frequency"))) return;
    Pattern derivPattern = CollaborativePruningComputer.getPattern(ex, deriv);
    if (derivPattern == null) return;

    extractPhrasePatternMatch(ex, deriv, derivPattern);
    extractPatternFrequency(ex, deriv, derivPattern);
  }

  public void extractPhrasePatternMatch(Example ex, Derivation deriv, Pattern derivPattern){
    if (!FeatureExtractor.containsDomain("phrase-pattern")) return;
    List<PhraseInfo> phraseInfos = PhraseInfo.getPhraseInfos(ex);
    for (PhraseInfo phraseInfo : phraseInfos) {
      deriv.addFeature("phrase-pattern", phraseInfo.text + ";" + derivPattern.pattern);
    }
  }

  public void extractPatternFrequency(Example ex, Derivation deriv, Pattern derivPattern) {
    if (!FeatureExtractor.containsDomain("pattern-frequency")) return;
    Integer freq = derivPattern.frequency;
    String level = "";
    if (freq <= 3)
      level = freq.toString();
    else if (freq <= 5)
      level = "4-5";
    else if (freq <= 9)
      level = "6-9";
    else{
      int floor = (int) (Math.floor(freq/10)*10);
      level = floor + "-" + (floor+10);
    }
    deriv.addFeature("pattern-freq", level);
    return;
  }
}
