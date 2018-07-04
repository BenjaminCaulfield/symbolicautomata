package benchmark.algebralearning;

import org.sat4j.specs.TimeoutException;

import algebralearning.equality.EqualityAlgebraLearnerFactory;
import algebralearning.sfa.SFAAlgebraLearner;
import algebralearning.sfa.SFAEquivalenceOracle;
import algebralearning.sfa.SFAMembershipOracle;
import automata.sfa.SFA;
import benchmark.SFAprovider;
import theory.characters.CharPred;
import theory.intervals.UnaryCharIntervalSolver;

/**
 * This class implements the first experiment (section 6.1) from the paper 
 * "The Learnability of Symbolic Automata" by George Argyros and Loris D'Antoni. 
 * 
 * @author George Argyros
 *
 */
public class RELearning {

	public static String[] reBenchmarks = {
			"\\<.*(script|xss).*?\\>",
			"\\<.*(applet|b(ase|gsound|link)|embed)[^\\>]*\\>",
			"(\\<[^\\<\\>]+\\>\\<[^<]+\\>\\<\\/[^\\<\\>]+\\>)",
			"(<meta[/+\\t\\n ].*?http-equiv[/+\\t\\n ])",
			"(<\\?import[/+\\t\\n ].*?implementation[/+\\t\\n ])",
			"(alter\\s*\\w+.*character\\s+set\\s+\\w+)|(\\\";\\s*waitfor\\s+time\\s+\\\")|(\\\"\\;.*:\\s*goto)",
			"(and)\\s+(\\d{1,10}|\\'[^\\=]{1,10}\\')\\s*?[\\=]|(and)\\s+(\\d{1,10}|\\'[^\\=]{1,10}\\')\\s*?[\\<\\>]|and\\s?(\\d{1,10}|[\\'\\\"][^\\=]{1,10}[\\'\\\"])\\s?[\\=\\<\\>]+|(and)\\s+(\\d{1,10}|\\'[^\\=]{1,10}\\')",
			"([^a-zA-Z]\\s+as\\s*[\\\"a-z0-9A-Z]+\\s*from)|([^a-zA-Z]+\\s*(union|select|create|rename|truncate|load|alter|delete|update|insert|desc))|((select|create|rename|truncate|load|alter|delete|update|insert|desc)\\s+((group_)concat|char|load\\_file)\\s?\\(?)",
			"(\\,\\s*(alert|showmodaldialog|eval)\\s*\\,)|(\\s*eval\\s*[^ ])|([^:\\s\\w\\,.\\/?+-]\\s*)?(\\<\\![a-z\\/\\_@])(\\s*return\\s*)?((document\\s*\\.)?(.+\\/)?(alert|eval|msgbox|showmod(al|eless)dialog|showhelp|prompt|write(ln)?|confirm|dialog|open))\\s*([^.a-z\\s\\-]|(\\s*[^\\s\\w\\,.@\\/+-]))|(java[ \\/]*\\.[ \\/]*lang)|(\\w\\s*\\=\\s*new\\s+\\w+)|(&\\s*\\w+\\s*\\)[^\\,])|(\\+[^a-zA-Z]*new\\s+\\w+[^a-zA-Z]*\\+)|(document\\.\\w)",
			"(union\\s*(all|distinct|[(\\!\\@]*)\\s*[([]*\\s*select)|(\\w+\\s+like\\s+\\\")|(like\\s*\\\"\\\\%)|(\\\"\\s*like\\W*[\\\"0-9])|(\\\"\\s*(n?and|x?or|not[ ]|\\|\\||\\\\&\\\\&)\\s+[ a-z0-9A-Z]+\\=\\s*\\w+\\s*having)|(\\\"\\s*\\*\\s*\\w+\\W+\\\")|(\\\"\\s*[^?a-z0-9A-Z \\=.,;)(]+\\s*[(\\@\\\"]*\\s*\\w+\\W+\\w)|(select\\s*[\\[\\]\\(\\)\\s\\w\\.,\\\"-]+from)|(find_in_set\\s*\\()",
			"(\\w|\\-)+\\@((\\w|\\-)+\\.)+(\\w|\\-)+",
			"\\$?(\\d{1,3}\\,?(\\d{3}\\,?)*\\d{3}(\\.\\d{0,2})?|\\d{1,3}(\\.\\d{0,2})?|\\.\\d{1,2}?)",
			"([A-Z]{2}|[a-z]{2}[ ]\\d{2}[ ][A-Z]{1,2}|[a-z]{1,2}[ ]\\d{1,4})?([A-Z]{3}|[a-z]{3}[ ]\\d{1,4})?",
			"[A-Za-z0-9](([ \\.\\-]?[a-zA-Z0-9]+)*)\\@([A-Za-z0-9]+)(([\\.\\-]?[a-zA-Z0-9]+)*)\\.[ ]([A-Za-z][A-Za-z]+)",
			"[\\+\\-]?([0-9]*\\.?[0-9]+|[0-9]+\\.?[0-9]*)([eE][\\+\\-]?[0-9]+)?"
	};		
		
	public static Integer[] learnREBenchmark(Integer index) throws TimeoutException {
		Integer[] results = new Integer[8];
		
		if (index < 0 || index >= reBenchmarks.length) {
			return null; 
		}
		UnaryCharIntervalSolver solver = new UnaryCharIntervalSolver();
		SFAprovider provider = new SFAprovider(reBenchmarks[index], solver);			
		SFA<CharPred, Character> model, sfa = provider.getSFA();
    	    		
		SFAMembershipOracle <CharPred, Character> memb = new SFAMembershipOracle<>(sfa, solver);  
		SFAEquivalenceOracle <CharPred, Character> equiv = new SFAEquivalenceOracle<>(sfa, solver); 
		EqualityAlgebraLearnerFactory <CharPred, Character> eqFactory = new EqualityAlgebraLearnerFactory <>(solver);
		SFAAlgebraLearner <CharPred, Character> learner = new SFAAlgebraLearner<>(memb, solver, eqFactory);
		model = learner.getModelFinal(equiv);
		// The results are saved in the order that they are presented in the paper.
		results[0] = model.stateCount();
		results[1] = model.getTransitionCount();
		results[2] = memb.getDistinctQueries();
		results[3] = equiv.getDistinctCeNum();
		results[4] = equiv.getCachedCeNum();
		results[5] = learner.getNumCEGuardUpdates();
		results[6] = learner.getNumDetCE();
		results[7] = learner.getNumCompCE();
  		return results;
	}
	
	public static Integer[][] learnAllBenchmarks() throws TimeoutException {
		Integer[][] results = new Integer[reBenchmarks.length][8];
	    for (int i = 0; i < reBenchmarks.length; i ++) {
	    	results[i] = learnREBenchmark(i);	    	
	    }
	    return results;
	}
}
