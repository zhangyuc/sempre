package edu.stanford.nlp.sempre.cprune;

import java.util.*;
import com.google.common.collect.Lists;
import edu.stanford.nlp.sempre.*;
import fig.basic.LispTree;
import fig.basic.LogInfo;
import fig.basic.MapUtils;

public class CustomGrammar extends Grammar{
	public static Set<String> baseCategories = new HashSet<String>(Arrays.asList("$Unary", "$Binary", "$Entity", "$Property"));
	
	ArrayList<Rule> baseRules = new ArrayList<>();
	
	Map<String, Integer> symbolicFormulas = new HashMap<>(); // symbolicFormula => symbolicFormula ID
	Map<String, Set<String>> customRules = new HashMap<>(); // indexedSymbolicFormula => customRuleString
	Map<String, Set<Rule>> customBinarizedRules = new HashMap<>(); // customRuleString => Binarized rules
	
	public List<Rule> getRules(Collection<String> customRuleStrings){
		Set<Rule> ruleSet = new LinkedHashSet<>();
		ruleSet.addAll(baseRules);
		for(String ruleString : customRuleStrings){
			ruleSet.addAll(customBinarizedRules.get(ruleString));
		}
		return new ArrayList<Rule>(ruleSet);
	}
	
	public static String safeReplace(String formula, String target, String replacement){
		formula = formula.replace(target + ")", replacement + ")");
		formula = formula.replace(target + " ", replacement + " ");
		/*if (target.equals("(number 1)")){
			formula = formula.replace(" (number 1) (number 1)", " @DoubleNumberOnes");
			formula = formula.replace(target + ")", replacement + ")");
			formula = formula.replace(target + " ", replacement + " ");
			formula = formula.replace(" @DoubleNumberOnes", " (number 1) (number 1)");
		}
		else{
			formula = formula.replace(target + ")", replacement + ")");
			formula = formula.replace(target + " ", replacement + " ");
		}*/
		return formula;
	}
	
	public void init(Grammar initGrammar){
		baseCategories.add(Rule.tokenCat);
		baseCategories.add(Rule.phraseCat);
		baseCategories.add(Rule.lemmaTokenCat);
	    baseCategories.add(Rule.lemmaPhraseCat);
	    
	    baseRules = new ArrayList<>();
		for (Rule rule: initGrammar.getRules()){
			if (baseCategories.contains(rule.lhs)){
				baseRules.add(rule);
			}
		}
		this.freshCatIndex = initGrammar.freshCatIndex;
	}
	
	public static void aggregateSymbols(Derivation deriv){
		if (deriv.treeSymbols != null) return;
		
		deriv.treeSymbols = new LinkedHashMap<>();
		if (baseCategories.contains(deriv.cat)){
			String formula = deriv.formula.toString();
			deriv.treeSymbols.put(formula, new Symbol(deriv.cat, formula, 1));
		}
		else{
			for (Derivation child: deriv.children){
				aggregateSymbols(child);
				for (Symbol symbol : child.treeSymbols.values()){
					if (deriv.treeSymbols.containsKey(symbol.formula)){
						deriv.treeSymbols.get(symbol.formula).frequency += symbol.frequency;
					}
					else{
						deriv.treeSymbols.put(symbol.formula, symbol);
					}
				}
			}
		}
	}
	
	private void computeCustomRules(Derivation deriv, Set<String> corssReferences){
		deriv.ruleSymbols = new LinkedHashMap<>();
		deriv.customRuleStrings = new ArrayList<>();
		String formula = deriv.formula.toString();
		
		if (baseCategories.contains(deriv.cat)){
			// Leaf node induces no custom rule
			deriv.containsCrossReference = corssReferences.contains(formula);
			// Propagate the symbol of this derivation to the parent
			deriv.ruleSymbols.putAll(deriv.treeSymbols);
		}
		else{
			deriv.containsCrossReference = false;
			for (Derivation child: deriv.children){
				computeCustomRules(child, corssReferences);
				deriv.containsCrossReference = deriv.containsCrossReference || child.containsCrossReference;
			}

			for (Derivation child: deriv.children){
				deriv.ruleSymbols.putAll(child.ruleSymbols);
				deriv.customRuleStrings.addAll(child.customRuleStrings);
			}
			
			if (deriv.containsCrossReference){
				// If this node contains a cross reference
				if (deriv.isRootCat()){
					// If this is the root node, then generate a custom rule
					deriv.customRuleStrings.add(getCustomRuleString(deriv));
				}
			}
			else{
				if (!deriv.cat.startsWith("$Intermediate")){
					// If it is not an intermediate node, generate a custom rule
					deriv.customRuleStrings.add(getCustomRuleString(deriv));
					
					// Propagate this derivation as a category to the parent
					deriv.ruleSymbols.clear();
					deriv.ruleSymbols.put(formula, new Symbol(hash(deriv), deriv.formula.toString(), 1));
				}
			}
		}
	}
	
	private String getCustomRuleString(Derivation deriv){
		String formula = deriv.formula.toString();
		List<Symbol> rhsSymbols = new ArrayList<>(deriv.ruleSymbols.values());
		for (Symbol symbol : rhsSymbols) symbol.computeIndex(formula);
		Collections.sort(rhsSymbols);
		
		String lhs = null;
		if (deriv.containsCrossReference)
			lhs = deriv.cat;
		else
			lhs = deriv.isRootCat() ? "$ROOT" : hash(deriv);
		
		
		LinkedList<String> rhsList = new LinkedList<>();
		int index = 1;
		for(Symbol symbol : rhsSymbols){			
			if (formula.equals(symbol.formula)){
				formula = "(IdentityFn)";
			}
			else{
//				formula = formula.replace(symbol.formula + ")", "(var s" + index + "))");
//				formula = formula.replace(symbol.formula + " ", "(var s" + index + ") ");
				formula = safeReplace(formula, symbol.formula, "(var s" + index + ")");
				formula = "(lambda s" + index + " " + formula + ")";
			}
			rhsList.addFirst(symbol.category);
			index += 1;
		}
		String rhs = null;
		if (rhsList.size() > 0){
			rhs = "(" + String.join(" ", rhsList) + ")";
		}
		else{
			rhs = "(nothing)";
			formula = "(ConstantFn " + formula + ")";
		}
		return "(rule " + lhs + " " + rhs + " " + formula + ")";
	}
	
	public String hash(Derivation deriv){
		if (baseCategories.contains(deriv.cat))
			return deriv.cat;
		
		String formula = getSymbolicFormula(deriv);
		if (!symbolicFormulas.containsKey(formula)){
			symbolicFormulas.put(formula, symbolicFormulas.size()+1);
			String hashString = "$Formula" + symbolicFormulas.get(formula);
			LogInfo.log("Add symbolic formula: " + hashString + " = " + formula + "  (" + deriv.cat + ")");
		}
		return "$Formula" + symbolicFormulas.get(formula);
	}
	
	public static String getSymbolicFormula(Derivation deriv) {		
		aggregateSymbols(deriv);
		String formula = deriv.formula.toString();
		for(Symbol symbol : deriv.treeSymbols.values()){
			if (formula.equals(symbol.formula)) formula = symbol.category;
//			formula = formula.replace(symbol.formula + " ", symbol.category + " ");
//			formula = formula.replace(symbol.formula + ")", symbol.category + ")");
			formula = safeReplace(formula, symbol.formula, symbol.category);
		}
		return formula;
	}
	
	public static String getIndexedSymbolicFormula(Derivation deriv) {
		return getIndexedSymbolicFormula(deriv, deriv.formula.toString());
	}
	
	public static String getIndexedSymbolicFormula(Derivation deriv, String formula) {
		aggregateSymbols(deriv);
		int index = 1;

		List<Symbol> symbolList = new ArrayList<>(deriv.treeSymbols.values());
		for (Symbol symbol : symbolList) symbol.computeIndex(formula);
		Collections.sort(symbolList);
		for(Symbol symbol : symbolList){
			if (formula.equals(symbol.formula)) formula = symbol.category + "#" + index;
//			formula = formula.replace(symbol.formula + " ", symbol.category + "#" + index + " ");
//			formula = formula.replace(symbol.formula + ")", symbol.category + "#" + index +")");
			formula = safeReplace(formula, symbol.formula, symbol.category + "#" + index);
			index += 1;
		}
		return formula;
	}
	
	public Set<String> addCustomRule(Derivation deriv, Example ex){
		String indexedSymbolicFormula = getIndexedSymbolicFormula(deriv);
		if (customRules.containsKey(indexedSymbolicFormula)){
			return customRules.get(indexedSymbolicFormula);
		}
		
		aggregateSymbols(deriv);
		Set<String> corssReferences = new HashSet<>();
		for (Symbol symbol: deriv.treeSymbols.values()){
			if (symbol.frequency > 1){
				corssReferences.add(symbol.formula);
			}
		}
		computeCustomRules(deriv, corssReferences);
		customRules.put(indexedSymbolicFormula, new HashSet<String>(deriv.customRuleStrings));
		
		LogInfo.begin_track("Add custom rules for formula: " + indexedSymbolicFormula);
		for(String customRuleString : deriv.customRuleStrings){			
			if (customBinarizedRules.containsKey(customRuleString)){
				LogInfo.log("Custom rule exists: " + customRuleString);
				continue;
			}
			
			rules = new ArrayList<>();
			LispTree tree = LispTree.proto.parseFromString(customRuleString);
			interpretRule(tree);
			customBinarizedRules.put(customRuleString, new HashSet<Rule>(rules));
			
			// Debug
			LogInfo.begin_track("Add custom rule: " + customRuleString);
			for(Rule rule: rules){
				LogInfo.log(rule.toString());
			}
			LogInfo.end_track();
		}
		LogInfo.end_track();
		
		// Debug
		System.out.println("consistent_lf\t" + ex.id + "\t" + deriv.formula.toString());
		
		return customRules.get(indexedSymbolicFormula); 
	}
	
	public static ArrayList<Rule> computeCatUnaryRules(Collection<Rule> rules) {
	    // Handle catUnaryRules
		ArrayList<Rule> catUnaryRules = new ArrayList<>();
	    Map<String, List<Rule>> graph = new HashMap<>();  // Node from LHS to list of rules
	    for (Rule rule : rules)
	      if (rule.isCatUnary())
	        MapUtils.addToList(graph, rule.lhs, rule);

	    // Topologically sort catUnaryRules so that B->C occurs before A->B
	    Map<String, Boolean> done = new HashMap<>();
	    for (String node : graph.keySet())
	    	traverse(catUnaryRules, node, graph, done);
	    return catUnaryRules;
	}
	
	// Helper function for transitive closure of unary rules.
    private static void traverse(List<Rule> catUnaryRules,
                          String node,
                          Map<String, List<Rule>> graph,
                          Map<String, Boolean> done) {
	    Boolean d = done.get(node);
	    if (Boolean.TRUE.equals(d)) return;
	    if (Boolean.FALSE.equals(d))
	      throw new RuntimeException("Found cycle of unaries involving " + node);
	    done.put(node, false);
	    for (Rule rule : MapUtils.getList(graph, node)) {
	      traverse(catUnaryRules, rule.rhs.get(0), graph, done);
	      catUnaryRules.add(rule);
	    }
	    done.put(node, true);
    }
}
