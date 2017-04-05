package edu.stanford.nlp.sempre.cprune;
import edu.stanford.nlp.sempre.*;

import java.util.*;

public class Pattern implements Comparable<Pattern>{
	public String pattern;
	public Integer rank;
	public Integer frequency;
	public Double score;
	
	public Pattern(String pattern, Integer rank, Integer frequency) {
	    this.pattern = pattern;
	    this.rank = rank;
	    this.frequency = frequency;
	}
	
	public Double complexity(){
		return (double) (pattern.length() - pattern.replace("(@R", "***").replace("(", "").length());
	}
	
	@Override
	public String toString(){
		return "(" + pattern + ", " + rank + ", " + frequency + ")";
	}

	@Override
	public int compareTo(Pattern arg0) {
		if (this.frequency > arg0.frequency){
			return -1;
		}
		else if (this.frequency < arg0.frequency){
			return 1;
		}
		else{
			return this.complexity().compareTo(arg0.complexity());
		}
	}
}
