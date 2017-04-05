package edu.stanford.nlp.sempre.cprune;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.*;
import java.util.Map.Entry;

import fig.basic.*;
import edu.stanford.nlp.sempre.*;
import fig.basic.LogInfo;

public class CollaborativePruningComputer {
	public static class Options {
	    @Option public boolean enableCollaborativePruning = false;
	    @Option public int maxNeighborRank = -1;
	    @Option public int K = -1;
	    @Option public int maxPredictedPatterns = -1;
	    @Option public String neighborFilePath = null;
	    @Option public String idPrefix = "";
	    
	    @Option(gloss = "Maximum number of derivations per example")
	    public int maxDerivations = 5000;
	    @Option(gloss = "Maximum number of exporation iterations")
	    public int maxExplorationIters = 5000;
	}
	
	public static Options opts = new Options();
	public enum Mode { EXPLORE, EXPLOIT }
	public static Mode mode;
	public static CollaborativePruningStats stats = new CollaborativePruningStats();
	public static CustomGrammar customGrammar = new CustomGrammar();
	
	// Global variables
	static Map<String, ArrayList<String>> neighbors;
	static Map<String, Pattern> consistentPattern = new HashMap<>(); // uid => pattern
	static Map<String, Set<String>> customRules = new HashMap<>(); // patternString => customRuleString
	static Set<String> allConsistentPatterns = new HashSet<>(); // set of patternStrings

	// Example-level variables
	public static boolean foundConsistentDerivation = false;
	public static Map<String, Pattern> predictedPatterns;
	public static List<Rule> predictedRules;
	public static List<Rule> predictedCatUnaryRules;
	
	public static void loadNeighbors(){
		if (opts.neighborFilePath == null){
			LogInfo.logs("neighborFilePath is null.");
			return;
		}
		
		LogInfo.logs("loading Neighbors " + opts.neighborFilePath);
		Map<String, ArrayList<String>> tmpMap = new HashMap<>(); 
		try {
			FileInputStream fis = null;
			fis = new FileInputStream(opts.neighborFilePath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fis));
            String line = reader.readLine();
            while(line != null){
            	String[] tokens = line.replace("\n", "").split("\t");
            	String uid = opts.idPrefix + tokens[0];
            	ArrayList<String> list = new ArrayList<>();
            	String[] neighborList = tokens[1].split(",");
            	for (int i = 0; i < neighborList.length; i++){
            		String neighbor = neighborList[i];
            		list.add(opts.idPrefix+neighbor);
            	}
            	tmpMap.put(uid, list);
            	line = reader.readLine();
            }
            reader.close();
		} catch (Exception e) {
			System.out.println("Error: cannot load from file.");
			e.printStackTrace();
		}
		neighbors = tmpMap;
	}
	
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue( Map<K, V> map)
	{
	    List<Map.Entry<K, V>> list =
	        new LinkedList<Map.Entry<K, V>>( map.entrySet() );
	    Collections.sort( list, new Comparator<Map.Entry<K, V>>()
	    {
	        public int compare( Map.Entry<K, V> o1, Map.Entry<K, V> o2 )
	        {
	            return (o1.getValue()).compareTo( o2.getValue() );
	        }
	    } );
	
	    Map<K, V> result = new LinkedHashMap<K, V>();
	    for (Map.Entry<K, V> entry : list)
	    {
	        result.put( entry.getKey(), entry.getValue() );
	    }
	    return result;
	}
	
	public static void initialize(Example ex, Mode mode){
		CollaborativePruningComputer.mode = mode;
		predictedRules = null;
		predictedCatUnaryRules = null;
		predictedPatterns = null;
		foundConsistentDerivation = false;
		if (mode == Mode.EXPLOIT){
			preprocessExample(ex);
		}
	}
	
	static void preprocessExample(Example ex){
		String uid = ex.id;
		
		Map<String, Pattern> patternFreqMap = new HashMap<>(); 
		int n = Math.min(opts.maxNeighborRank, neighbors.get(uid).size());
		int total = 0;
		
		if (opts.K > 0){
			for (int i = 0; i < n; i++){
				String nid = neighbors.get(uid).get(i);
				if (!consistentPattern.containsKey(nid))
					continue;
				
				String neighborPattern = consistentPattern.get(nid).pattern;
				if (!patternFreqMap.containsKey(neighborPattern))
					patternFreqMap.put(neighborPattern, new Pattern(neighborPattern, 0, 0));
				patternFreqMap.get(neighborPattern).frequency += 1;
				total += 1;
				if (total >= opts.K)
					break;
			}
		}
		else{
			for (String patternString : allConsistentPatterns){
				patternFreqMap.put(patternString, new Pattern(patternString, 0, 1));
			}
		}
		
		patternFreqMap = sortByValue(patternFreqMap);
		Integer rank = 0;

		Set<String> predictedRulsStrings = new HashSet<>();
		predictedPatterns = new HashMap<>();
		LogInfo.begin_track("Predicted patterns");
		for (Entry<String, Pattern> entry : patternFreqMap.entrySet()){
			Pattern newPattern = entry.getValue();
			predictedPatterns.put(newPattern.pattern, newPattern);
			predictedRulsStrings.addAll(customRules.get(newPattern.pattern));
			LogInfo.logs((rank+1) + ". " + newPattern.pattern + " (" + newPattern.frequency + ")");
			rank += 1;
			if (rank >= opts.maxPredictedPatterns)
				break;
		}
		predictedRules = customGrammar.getRules(predictedRulsStrings);
		predictedCatUnaryRules = CustomGrammar.computeCatUnaryRules(predictedRules);
		LogInfo.end_track();
	}
	
	public static String getPatternString(Derivation deriv){
		if (deriv.cat.equals("$TOKEN") || deriv.cat.equals("$PHRASE") || deriv.cat.equals("$LEMMA_TOKEN") || deriv.cat.equals("$LEMMA_PHRASE")){
			return deriv.cat;
		}
		else{
			return PatternInfo.convertToIndexedPattern(deriv);
		}	
	}
	
	public static void addRules(String patternString, Derivation deriv, Example ex){
		if (!customRules.containsKey(patternString)){
			customRules.put(patternString, new HashSet<String>());
		}
		Set<String> parsedCustomRules = customGrammar.addCustomRule(deriv, ex);
		customRules.get(patternString).addAll(parsedCustomRules);
	}
	
	public static void updateConsistentPattern(ValueEvaluator evaluator, Example ex, Derivation deriv){
		String uid = ex.id;
		if (ex.targetValue != null)
	        deriv.compatibility = evaluator.getCompatibility(ex.targetValue, deriv.value);
		
		if (deriv.isRootCat() && deriv.compatibility == 1){
			foundConsistentDerivation = true;
			
			String patternString = getPatternString(deriv);
			Pattern newConsistentPattern = new Pattern(patternString, 0, 0);
			newConsistentPattern.score = deriv.getScore();
			
			if (!consistentPattern.containsKey(uid)){
				addRules(patternString, deriv, ex);
				consistentPattern.put(uid, newConsistentPattern);
				allConsistentPatterns.add(patternString);
			}
			else{
				Pattern oldConsistentPattern = consistentPattern.get(uid);
				if (newConsistentPattern.score > oldConsistentPattern.score)
				{
					addRules(patternString, deriv, ex);
					consistentPattern.put(uid, newConsistentPattern);
					allConsistentPatterns.add(patternString);
				}
			}
		}
	}
	
	public static Pattern getConsistentPattern(Example ex){
		if (consistentPattern.containsKey(ex.id))
			return consistentPattern.get(ex.id);
		else
			return null;
	}
	
	public static Pattern getPattern(Example ex, Derivation deriv) {
		if (mode == Mode.EXPLORE)
			return null;
		
		if (!deriv.isRootCat())
			return null;
			
		return predictedPatterns.get(getPatternString(deriv));
	}
}
