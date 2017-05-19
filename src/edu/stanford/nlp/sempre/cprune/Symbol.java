package edu.stanford.nlp.sempre.cprune;

public class Symbol implements Comparable<Symbol>{
  String category;
  String formula;
  Integer frequency;
  Integer index;

  public Symbol(String category, String formula, int frequency) {
      this.category = category;
      this.formula = formula;
      this.frequency = frequency;
  }

  public void computeIndex(String referenceString){
    index = referenceString.indexOf(formula);
    if (index < 0){
      index = Integer.MAX_VALUE;
    }
    /*
    if (formula.equals("(number 1)")){
      referenceString = referenceString.replace(" (number 1) (number 1)", "                      ");
    }
    Integer index1 = referenceString.indexOf(formula + ")");
    Integer index2 = referenceString.indexOf(formula + " ");
    index1 = (index1 < 0) ? Integer.MAX_VALUE : index1;
    index2 = (index2 < 0) ? Integer.MAX_VALUE : index2;
    index = Math.min(index1, index2);
    */
  }

  @Override
  public int compareTo(Symbol arg0) {
    return index.compareTo(arg0.index);
  }
}
