import java.util.List;
import java.util.stream.Collectors;


public class FairMatching {
	
	// Timer variables for tracking algorithm performance
	long startTime = 0;
	long endTime = 0;
	
	private void SetStart() { startTime = System.nanoTime(); }
	private void SetEnd() { endTime = System.nanoTime(); }
	
	public long GetTimeNS() {
		return endTime - startTime;
	}
	
	public double GetTimeMS() {
		long time_ns = GetTimeNS();
		double time_ms = (double)time_ns / 1000000.0;
		return time_ms;
	}
	
	// Debugging display bitmask for controlling output
	public static final int DEBUG_PRINT_NONE = 0;
	public static final int DEBUG_PRINT_PREF_LIST = 1 << 0; // Displays the original preference list at various stages
	public static final int DEBUG_PRINT_FEAS_LIST = 1 << 1; // Displays the feasible preference list at various steps
	public static final int DEBUG_PRINT_MAN_OPT_MATCHING = 1 << 2; // Displays the man optimal matching from Gale Shapley
	public static final int DEBUG_PRINT_WOMAN_OPT_MATCHING = 1 << 3; // Displays the woman optimal matching from Gale Shapley
	public static final int DEBUG_PRINT_FEASIBLE_RANGE = 1 << 4; // Displays the initial feasible preference index range after optimal / pessimal trim
	public static final int DEBUG_PRINT_FEASIBLE_FROM_TRIM = 1 << 5; // Displays the feasible pref list after doing the optimal / pessimal trim
	
	public static final int DEBUG_PRINT_ALL = 0xFFFFFFFF;
		
	
	// Heuristic Step Bitmask, indicates steps to be used for finding optimal solution
	public static final int TRIM_SINGLE_FEASIBLE = 1 << 0; // If a man or woman has a single feasible option, trim all others from having them as an option
	public static final int TRIM_MUTUAL_FEASIBLE = 1 << 1; // trim feasible options that are not mutual between all men and women
	public static final int TRIM_ALL = TRIM_SINGLE_FEASIBLE | TRIM_MUTUAL_FEASIBLE;
	
	
	
	//private List<List<Person>> groups = List.of();
	private List<Person> menGroup = List.of();
	private List<Person> womenGroup = List.of();
	
	private List<List<Person>> finalMatching = List.of();
	
	/** 
	 * This function will reset any class data, it is intended to be called before doing a new matching
	 */
	public void ResetData()
	{
		menGroup = List.of();
		womenGroup = List.of();
		finalMatching = List.of();
	}

	public static void main(String[] args) {
		// This will take a list of files, and run the algorithm on each of them
		List<String> inputs = List.of(
				"input.txt" 
				, "input3.txt" 
				, "input4.txt"
				, "test3.txt"
				);
		
		FairMatching fm = new FairMatching();
		
		System.out.println("Starting Fair Matching...");
		for (String fileN : inputs) {
			System.out.println("*************************************");
			System.out.println("Loading input file " + fileN);
			
			fm.PerformMatching(fileN, TRIM_ALL, DEBUG_PRINT_NONE);	
			System.out.println("*************************************");
		}
		System.out.println("*************************************");
		System.out.println("Fair Matching complete...");

	}

	public void PerformMatching(String filename, int trimMask, int debugMask) {
		
		ResetData();
		
		Matching unPairedMatching = InputParserUtility.ParseInput(filename);
		
		SetStart();
		
		Matching manOptimalMatch = GaleShapelyAlgorithm.execute(unPairedMatching.clone(), "m");
		Matching womanOptimalMatch = GaleShapelyAlgorithm.execute(unPairedMatching.clone(), "w");
		
		SetEnd();
		long gs_time_ns = GetTimeNS();
		double gs_time_ms = GetTimeMS();
		
		menGroup = unPairedMatching.getMen();
		womenGroup = unPairedMatching.getWomen();
		
		if ((debugMask & DEBUG_PRINT_MAN_OPT_MATCHING) > 0) {
			PrintMatching(manOptimalMatch, "Man Optimal Matching", true);
		}
		if ((debugMask & DEBUG_PRINT_WOMAN_OPT_MATCHING) > 0) {
			PrintMatching(womanOptimalMatch, "Woman Optimal Matching", false);
		}
		
		System.out.println("Trimming has begun...");
		
		SetStart();
		// trim the entries which are outside the optimal and pessimistic "bounds"
		trimAllFeasiblePreferences(manOptimalMatch, womanOptimalMatch, debugMask);
		
		if ((trimMask & TRIM_SINGLE_FEASIBLE) > 0) {
			// Remove matches that have only a single "possible" match
			trimSingleFeasible();			
		}

		
		// Print the curretly possible Preference pairs
	//	PrintPrefPossible(false);
		
		if ((trimMask & TRIM_MUTUAL_FEASIBLE) > 0) {
			// Trim options which are not feasible from the opposite group
			trimOppositeGroupFeasible();
		}
		
		
		// Print the curretly possible Preference pairs
	//	PrintPrefPossible(false);
		
		if ((trimMask & TRIM_SINGLE_FEASIBLE) > 0) {
			// Remove matches that have only a single "possible" match
			trimSingleFeasible();
		}
				
		// Print the curretly possible Preference pairs
		PrintPrefPossible(false);
				
		System.out.println("Starting Equitable Matcher ...");
		EquitableMatcher em = new EquitableMatcher();
		em.findAllMatchings(manOptimalMatch, false);
		
		SetEnd();
		
		long equitable_time = GetTimeNS();
		double equitable_time_ms = GetTimeMS();
		
//		em.printResults(manOptimalMatch, womanOptimalMatch);
		
		long manEquityScore = StableMatchingUtils.calculateEquityScore(manOptimalMatch);
		long womanEquityScore = StableMatchingUtils.calculateEquityScore(womanOptimalMatch);
		
		System.out.println("Man Optimal Equitable Score - " + manEquityScore + 
				" :: Woman Optimal Equitable Score - " + womanEquityScore);
		
		StoreMatchingResults(em.GetBestMatching(), manOptimalMatch, womanOptimalMatch, gs_time_ns, equitable_time );

	}
	
	private void StoreMatchingResults(Matching bestMatch, Matching manOptMatch, Matching womanOptMatch, 
			long GS_time_ns, long equityTime_ns)
	{
		
		int manManOpt = GetEquityScore(manOptMatch, true);
		int womanManOpt = GetEquityScore(manOptMatch, false);
		
		int manWomanOpt = GetEquityScore(womanOptMatch, true);
		int womanWomanOpt = GetEquityScore(womanOptMatch, false);
		
		System.out.println("For Man Optimal - the man equity score is " + manManOpt + " and woman equity score is " + 
		womanManOpt + " :: with a total equity score of " + (manManOpt + womanManOpt));
		System.out.println("For Woman Optimal - the man equity score is " + manWomanOpt + " and woman equity score is " + 
				womanWomanOpt + " :: with a total equity score of " + (manWomanOpt + womanWomanOpt));
	}
	
	/**
	 * This method will compute a specific gender equity score
	 * @param match The matching to be evaluated
	 * @param getMan determines if the man equity or woman equity score should be calculated
	 * @return Sum of the man or woman equity score
	 */
	private int GetEquityScore(Matching match, boolean getMan) 
	{
		int score = 0;
		List<Person> grp = (getMan ? match.getMen() : match.getWomen());
		for (Person p : grp) {
		//	System.out.println("Person " + p.getPosition() + " has match of " + p.getMatch().getPosition());
			score += p.getPreferenceWeight(p.getMatch());
		}		
		return score;
	}
	
	public void trimOppositeGroupFeasible()
	{
		System.out.println("***************************************************************");
		System.out.println("Removing possible matches based on non-mutual feasible lists...");
		trimMutualFeasibleLists(menGroup, womenGroup);
		trimMutualFeasibleLists(womenGroup, menGroup);
		
	}
	
	private void trimMutualFeasibleLists(List<Person> grpA, List<Person> grpB)
	{
		int count = 0;
		// Go through each person in group A
		for (Person p : grpA) {
			List<Integer> feasList = p.getFeasiblePreferences();
			// Go through each feasible match, and ensure that person also has person "p"
			//  	in their feasible list as well
			for (Integer f : feasList) {
				// If they don't have p in their list, mark them as infeasible also
				if (!grpB.get(f).getFeasiblePreferences().contains(p.getPosition())) {
					p.markInfeasible(f);
					count++;
				}
			}
		}
		System.out.println("Removed " + count + " infeasible non-mutual matches ...");
	}
	
	public void trimSingleFeasible()
	{
		System.out.println("Trimming Unique Matches from feasible lists...");
		trimUniqueMatch(menGroup, womenGroup);
		trimUniqueMatch(womenGroup, menGroup);
	}
	
	public void trimUniqueMatch(List<Person> grpA, List<Person> grpB) 
	{
		for (Person p : grpA) {
			List<Integer> prefs = p.getFeasiblePreferences();
			int cnt = prefs.size();
			if (cnt == 1) {
				// Person p only has 1 possible match, and their position is "matchNum"
				int matchNum = prefs.get(0);
			//	System.out.println("Unique Match found: " + p.getPosition() + ", " + matchNum);
				for (Person pers : grpA) 
				{
					// Ensure all other members of grpA have matchNum marked as infeasible
					if (!pers.equals(p)) {
						pers.markInfeasible(matchNum);
					}
				}
								
			}				
		}
		
	}
	
	private void PrintPrefPossible(List<Person> groupA, List<Person> groupB, List<Person> aOptimal,
			List<Person> aPessimal) {
		
		boolean isMale = true;
		if (groupA.size() > 0) {
			isMale = groupA.get(0).GetIsMale();
		}
		
		int cnt = groupA.size();
		
		// Loop through each person
		for (Person p : groupA) {
			int index = p.getPosition();
			int optNum = aOptimal.get(index).getMatch().getPosition();
			int pesNum = aPessimal.get(index).getMatch().getPosition();
			
			List<Integer> prefList = p.getPreferenceList();
			String options = "";
			boolean append = false;
			for (int i = 0; i < cnt; ++i) {
				int pr = prefList.get(i);
				if (pr == optNum) {
					append = true;
				}
				if (append) {
					options += " " + pr + "(" + i + ") ";
					
				}
				if (pr == pesNum) {
					break;
				}
			}
			
			System.out.println((isMale ? "M" : "W") + index + ": Optimal: " + optNum + " Pessimal: " + pesNum + 
					" ---- [ " + options + " ]");
			
		}
		
		
	}

	private void PrintPrefPossible(boolean printFullPrefs)
	{
		PrintGroupPrefPossible(menGroup, "M", printFullPrefs);
		PrintGroupPrefPossible(womenGroup, "F", printFullPrefs);
	}
	
	private void PrintGroupPrefPossible(List<Person> grp, String gend, boolean printFullPrefs)
	{
		System.out.println("Feasible Preferences for all " + gend);
		for (Person p : grp) {
			int index = p.getPosition();
			System.out.println(gend + index + (printFullPrefs ? " pref: " + p.getPreferenceList() : "" ) +
					" feas: " + p.getFeasiblePreferences());
		}
	}
	
	private void trimAllFeasiblePreferences(Matching manOptimalMatch, Matching womanOptimalMatch, 
			int debugMask) {
		//List<Person> men = menGroup; // groups.get(0);
		//List<Person> women = womenGroup; // groups.get(1);
		List<Person> menOptimal = manOptimalMatch.getMen();
		List<Person> menPessimal = womanOptimalMatch.getWomen();
		List<Person> womenOptimal = womanOptimalMatch.getWomen();
		List<Person> womenPessimal = manOptimalMatch.getWomen();

		
//		PrintPrefPossible(men, women, menOptimal, menPessimal);
//		PrintPrefPossible(women, men, womenOptimal, womenPessimal);
		
		trimFeasiblePreferencesByGender(menGroup, womenGroup, menOptimal, menPessimal, debugMask);
		trimFeasiblePreferencesByGender(womenGroup, menGroup, womenOptimal, womenPessimal, debugMask);
	}

	private void trimFeasiblePreferencesByGender(List<Person> groupA, List<Person> groupB, 
			List<Person> aOptimal, List<Person> aPessimal, int debugMask) {
		boolean isMale = true;
		if (groupA.size() > 0) {
			isMale = groupA.get(0).GetIsMale();
		}
		String gender = isMale ? "Males" : "Females";
		System.out.println("Trimming Feasible Preferences by Gender");
		System.out.println("Feasible Preferences for all " + gender);
		for (int personIndex = 0; personIndex < groupA.size(); personIndex++) {
			Person person = groupA.get(personIndex);
			Person optimalMatch = aOptimal.get(personIndex).getMatch();
			Person pessimalMatch = aPessimal.get(personIndex).getMatch();
			Integer optimalMatchIndex = groupB.indexOf(optimalMatch);
			Integer pessimalMatchIndex = groupB.indexOf(pessimalMatch);
			trimFeasiblePreferencesForIndividual(person, optimalMatchIndex, pessimalMatchIndex);
			
			if ((debugMask & (DEBUG_PRINT_FEASIBLE_RANGE | DEBUG_PRINT_PREF_LIST | DEBUG_PRINT_FEASIBLE_FROM_TRIM )) > 0 ) {
				String debugString = (isMale ? "M" : "F") + personIndex;
				if ((debugMask & DEBUG_PRINT_FEASIBLE_RANGE) > 0) {
					debugString += " Opt/Pess [ " + aOptimal.get(personIndex).getMatch().getPosition() +
							" -> " + aPessimal.get(personIndex).getMatch().getPosition() + " ]";
				}
				if ((debugMask & DEBUG_PRINT_PREF_LIST) > 0) {
					debugString += " pref: " + person.getPreferenceList();				
				}
				
				if ((debugMask & DEBUG_PRINT_FEASIBLE_FROM_TRIM) > 0) {
					debugString += " feas: " + person.getFeasiblePreferences();				
				}			
				System.out.println(debugString);				
			}
			
		}
	}
	
	private void trimFeasiblePreferencesForIndividual(Person person, Integer optimalMatchIndex,
			Integer pessimalMatchIndex) {
		boolean feasibleRange = false;
		for (Integer preference : person.getPreferenceList()) {
			if (preference.equals(optimalMatchIndex)) {
				feasibleRange = true;
			}
			if (!feasibleRange) {
				person.markInfeasible(preference);
			}
			if (preference.equals(pessimalMatchIndex)) {
				feasibleRange = false;
			}
		}
	}
	
	
	public void PrintMatching(Matching matching, String title, boolean menFirst) {
		System.out.println("Results of " + title);
		StableMatchingUtils.printOutput(matching, menFirst);
	}
	
//	public void PrintPreferences(List<Person> group, String gender)
//	{
//		System.out.println("Preference List for " + gender);
//		for (Person p : group) 
//		{
//			System.out.println((p.GetIsMale() ? "M" : "F") + p.getPosition() + " pref: " +
//					 p.getPreferenceList());
//		}
//	}
	
}
