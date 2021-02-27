package MyWork;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.Random;
import java.util.Vector;

import org.cloudbus.cloudsim.cloudlets.Cloudlet.Status;

import com.mechalikh.pureedgesim.DataCentersManager.DataCenter;
import com.mechalikh.pureedgesim.ScenarioManager.SimulationParameters;
import com.mechalikh.pureedgesim.ScenarioManager.SimulationParameters.TYPES;
import com.mechalikh.pureedgesim.SimulationManager.SimLog;
import com.mechalikh.pureedgesim.SimulationManager.SimulationManager;
import com.mechalikh.pureedgesim.TasksGenerator.Task;
import com.mechalikh.pureedgesim.TasksOrchestration.Orchestrator;

import net.sourceforge.jFuzzyLogic.FIS;
import src.com.fdtkit.fuzzy.data.Attribute;
import src.com.fdtkit.fuzzy.data.Dataset;
import src.com.fdtkit.fuzzy.data.Row;
import src.com.fdtkit.fuzzy.fuzzydt.FuzzyDecisionTree;
import src.com.fdtkit.fuzzy.fuzzydt.TreeNode;
import src.com.fdtkit.fuzzy.utils.AmbiguityMeasure;
import src.com.fdtkit.fuzzy.utils.LeafDeterminer;
import src.com.fdtkit.fuzzy.utils.LeafDeterminerBase;
import src.com.fdtkit.fuzzy.utils.PreferenceMeasure;

/**
 * This class evaluates the algorithm proposed in:
 * 
 * Mechalikh, C., Taktak, H., & Moussa, F. (2020, April). A Fuzzy Decision Tree
 * Based Tasks Orchestration Algorithm for Edge Computing Environments. In
 * International Conference on Advanced Information Networking and Applications
 * (pp. 193-203). Springer, Cham.
 * 
 * Which is based on Contextual non-stationary Multi-Armed-Bandit Against the
 * Fuzzy-Logic based algorithm
 * 
 * @author Mechalikh
 * 
 */
public class CustomOrchestrator extends Orchestrator {

	/** Fuzzy decision tree algorithm variables **/
	// stage 1 decision tree (root)
	private TreeNode root;
	FuzzyDecisionTree fuzzydecisionTree;

	// stage 2 decision tree (root)
	private TreeNode root2;
	FuzzyDecisionTree fuzzydecisionTree2;

	// Q tables
	Vector<Q_table_row> Q_Table = new Vector<Q_table_row>(); // stage 1
	Vector<Q_table_row> Q_Table2 = new Vector<Q_table_row>();// stage 2

	/** Fuzzy logic algorithm variables **/
	FIS fis; // fuzzy inference system of stage 1
	FIS fis2; // fuzzy inference system of stage 2

	public CustomOrchestrator(SimulationManager simulationManager) throws IOException {
		super(simulationManager);
		// reset dataset files
		UpdateFuzzyDecisionTree(Q_Table, 1);
		UpdateFuzzyDecisionTree(Q_Table2, 2);
	}

	@Override
	protected int findVM(String[] architecture, Task task) {
		// if the selected algorithm is the fuzzy decision tree-based one.
		if (simulationManager.getScenario().getStringOrchAlgorithm().equals("FDT"))
			try {
				// generate the fuzzy deceision trees of stage 1 and 2
				fuzzydecisionTree = getDecisionTree(1);
				fuzzydecisionTree2 = getDecisionTree(2);

				// offload the task
				return DecisionTree(architecture, task);
			} catch (Exception e) {
				e.printStackTrace();
			}
		// if the selected algorithm is the Fuzzy Logic-based one.
		else if (simulationManager.getScenario().getStringOrchAlgorithm().equals("FUZZY_LOGIC")) {
			String fileName = "PureEdgeSim/MyWork/stage1.fcl";
			fis = FIS.load(fileName, true);
			// Error while loading?
			if (fis == null) {
				System.err.println("Can't load file: '" + fileName + "'");
				return -1;
			}
			String fileName2 = "PureEdgeSim/MyWork/stage2.fcl";
			fis2 = FIS.load(fileName2, true);
			// Error while loading?
			if (fis2 == null) {
				System.err.println("Can't load file: '" + fileName2 + "'");
				return -1;
			}
			return FuzzyLogic(architecture, task);
		} else {
			SimLog.println("");
			SimLog.println("Custom Orchestrator- Unknnown orchestration algorithm '" + algorithm
					+ "', please check the simulation parameters file...");
			// Cancel the simulation
			Runtime.getRuntime().exit(0);
		}
		return -1;
	}

	private int DecisionTree(String[] architecture, Task task) throws Exception {
		// To keep track of the rule which has been used to offlaod the task
		// i.e the state in which the task has been offloaded
		String rule = "";

		// Fuzzification of input variables
		double[] lat = getLat(task.getMaxLatency());
		double[] tasklength = getTaskLength(task.getLength());
		double[] wanusage = getWanUsage(simulationManager.getNetworkModel().getWanUtilization());
		double[] mobi = getMobi(task.getEdgeDevice().getMobilityManager().isMobile());

		// Get average edge data centers resource utilization
		double vmUsage = 0;
		double count = 0;
		for (int i = 0; i < vmList.size(); i++) {
			if (((DataCenter) vmList.get(i).getHost().getDatacenter())
					.getType() == SimulationParameters.TYPES.EDGE_DATACENTER) {
				vmUsage += ((DataCenter) vmList.get(i).getHost().getDatacenter()).getResources()
						.getCurrentCpuUtilization();
				count++;
			}
		}
		vmUsage = vmUsage / count;
		double[] destusage = getDesCPUusage(vmUsage * 15);

		// save the rule used for the classification of this task, in oder to use it
		// later when updating the Q values
		rule = getLatTerm(task.getMaxLatency()) + ","
				+ getWanUsageTerm(simulationManager.getNetworkModel().getWanUtilization()) + ","
				+ getTaskLengthTerm(task.getLength()) + ","
				+ (task.getEdgeDevice().getMobilityManager().isMobile() ? "high" : "low") + ","
				+ getDesCPUUsageTerm(vmUsage * 15);

		int action;
		double random = new Random().nextFloat();
		if (random <= 6 / simulationManager.getSimulation().clock()) { // explore
			action = new Random().nextInt(3); // pickup a random action (between cloud, edge, or mist)
		} else {// exploit
			// classify the state+ action "cloud"
			Dataset d = getDataset("cloud", lat, wanusage, tasklength, mobi, destusage);
			double[] cVals_cloud = fuzzydecisionTree.classify(0, d, "Offload", fuzzydecisionTree.generateRules(root));

			// classify the state+ action "edge"
			Dataset d2 = getDataset("edge", lat, wanusage, tasklength, mobi, destusage);
			double[] cVals_edge = fuzzydecisionTree.classify(0, d2, "Offload", fuzzydecisionTree.generateRules(root));

			// classify the state+ action "mist"
			Dataset d3 = getDataset("mist", lat, wanusage, tasklength, mobi, destusage);
			double[] cVals_mist = fuzzydecisionTree.classify(0, d3, "Offload", fuzzydecisionTree.generateRules(root));

			// compare the classification results and get the best action/ offloading
			// destination
			action = getDecision(cVals_cloud, cVals_edge, cVals_mist);
		}

		if (action == 1) {// the cloud is the best action
			rule = "cloud," + rule;
			// save the rule in the task metadata for later use
			task.setMetaData(new String[] { rule, "" });
			// the offloading decision is the Cloud so offlaod the task to the cloud.
			// to do this, we call another algorithm, and tell it to use the cloud-only
			// architecture, in order to get the best cloud vm

			String[] architecture2 = { "Cloud" };
			return LatencyAndEnergyAware(architecture2, task);
		} else if (action == 2) {
			rule = "edge," + rule;
			// save the rule in the task metadata for later use
			task.setMetaData(new String[] { rule, "" });

			// select best edge vm using another algorithm
			String[] architecture2 = { "Edge" };
			return LatencyAndEnergyAware(architecture2, task);
		} else {
			rule = "mist," + rule;
			// save the rule in the task metadata for later use
			task.setMetaData(new String[] { rule, "" });

			// enter to stage 2, and classify edge devices
			String[] architecture2 = { "Mist" };
			return Stage2decisiontree(architecture2, task);
		}

	}

	/**
	 * this function compares the fuzzy output and returns the best action in stage
	 * 1
	 **/
	private int getDecision(double[] cVals_cloud, double[] cVals_edge, double[] cVals_mist) {
		// compare the membership degrees of fuzzy set "high"
		if (cVals_edge[0] >= cVals_cloud[0] && cVals_edge[0] > cVals_mist[0])
			return 2; // edge
		if (cVals_mist[0] >= cVals_cloud[0] && cVals_mist[0] >= cVals_edge[0])
			return 3; // mist

		// compare the membership degrees of fuzzy set "medium"
		if (cVals_edge[1] >= cVals_cloud[1] && cVals_edge[1] > cVals_mist[1])
			return 2; // edge
		if (cVals_mist[1] >= cVals_cloud[1] && cVals_mist[1] >= cVals_edge[1])
			return 3;// mist

		// compare the membership degrees of fuzzy set "low"
		if (cVals_edge[2] >= cVals_cloud[2] && cVals_edge[2] > cVals_mist[2])
			return 2; // edge
		if (cVals_mist[2] >= cVals_cloud[2] && cVals_mist[2] >= cVals_edge[2])
			return 3;// mist

		return 1; // cloud

	}

	/** stage 2 of the proposed algorithm, which classifies the edge devices **/
	private int Stage2decisiontree(String[] architecture2, Task task) throws Exception {
		double max = -1;
		int vm = -1;
		String rule = "";
		for (int i = 0; i < orchestrationHistory.size(); i++) {
			if (offloadingIsPossible(task, vmList.get(i), architecture2)
					&& vmList.get(i).getStorage().getCapacity() > 0) {

				double[] destcpu = getDesCPU(
						((DataCenter) vmList.get(i).getHost().getDatacenter()).getResources().getCurrentCpuUtilization()
								* 100);
				double[] tasklength = getTaskLength(task.getLength());
				double[] destremai = getDestRemain((DataCenter) vmList.get(i).getHost().getDatacenter());
				double[] desmobi = this.getDestMobi((DataCenter) vmList.get(i).getHost().getDatacenter(),
						task.getEdgeDevice().getMobilityManager().isMobile());
				Dataset d2 = getDataset2(destcpu, tasklength, destremai, desmobi);

				double[] cVals = fuzzydecisionTree2.classify(0, d2, "Offload", fuzzydecisionTree2.generateRules(root2));

				// to save the rule used to offload this task, this rule will be used later when
				// updating the Q values
				// the rule will be stored in the task metadata object for easy access
				rule = getDesCPUTerm(
						((DataCenter) vmList.get(i).getHost().getDatacenter()).getResources().getCurrentCpuUtilization()
								* 100)
						+ "," + getTaskLengthTerm(task.getLength()) + ","
						+ getDestRemainTerm((DataCenter) vmList.get(i).getHost().getDatacenter()) + ","
						+ getDestMobiTerm((DataCenter) vmList.get(i).getHost().getDatacenter(),
								task.getEdgeDevice().getMobilityManager().isMobile());
				// if the class = high, high = the expected success rate is high or the
				// suitability of the device, so we should offload to this device
				// cVals is an array in which the fuzzified values are stored.
				// cVals[0]= the membership of degree of fuzzy set "high"
				// cVals{1] is for the fuzzy set "medium"
				// and cVals[2] is for the low one.
				if (cVals[0] >= cVals[1] && (max == -1 || max < cVals[0] + 2)) {
					// if the membership degree of "high" is bigger than "medium"(
					// cVals[0]>=cVals[1]) and if it is the first time (max==-1), or it is not the
					// first time but the old max is below the membership degree of "high".
					// then select the this device/vm as to execute the task
					vm = i;
					// update the max value with the new membership degree of "high" fuzzy set
					// if the wake it here, this means that we have found at least one device that
					// is classifed as "high".This means that any other device that is classified as
					// "medium" or "low" should be ignored. Since we are using a signle variable
					// "max" to store the highest membership degree for any of those fuzzy sets
					// (high, medium, low). We need to keep the superiority of "high" over "medium"
					// and "medium" over "low".
					// e.g., if the classification of a device gives 0.6 for the "high" fuzzy set.
					// and the next one gives 1 but for "low" fuzzy set. In this case 0.6>1, to do
					// this we simply add 2 to 0.6, so we will have 2.6 >
					// 1.
					max = cVals[0] + 2;

				} // if the class = medium
				else if (cVals[1] >= cVals[2]) {
					if (max == -1 || max < cVals[1] + 1) {
						max = cVals[0] + 1;
						vm = i;
					}
				} else {
					// the class= low
					if (max == -1 || max < cVals[2]) {
						max = cVals[2];
						vm = i;
					}
				}

			}

		}
		if (vm != -1) {
			String[] rules = (String[]) task.getMetaData();
			// save the second rule (used in stage 2)
			rules[1] = rule;
			// save the rule in the task metadata for later use
			task.setMetaData(rules);
			// return the offloading destination
			return vm;
		} else {
			// If the device moved away from the orchestrator, the task will fail. Unless it
			// offloads it by itself.
			// to do this we use another another algorithm to select the closest edge
			// server. just in case.
			String[] architecture = { "Edge" };
			return LatencyAndEnergyAware(architecture, task);
		}
	}

	private Dataset getDataset2(double[] destcpu, double[] tasklength, double[] destremai, double[] desmobi) {
		Dataset d = new Dataset("Sample1");

		// Add the attributes with Linguistic terms
		d.addAttribute(new Attribute("DestCPU", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("TaskLength", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("DestRem", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("DestMob", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("Offload", new String[] { "High", "Medium", "Low" }));
		double[][] columns = { destcpu, tasklength, destremai, desmobi };

		d.addRow(new Row(new Object[] { "Dummy", "Dummy", "Dummy", "Dummy", "Dummy" }, columns));

		return d;
	}

	private double[] getMobi(boolean mobile) {
		if (mobile)
			return new double[] { 1.0, 0.0, 0.0 };
		return new double[] { 0.0, 0.0, 1.0 };
	}

	private String getLatTerm(double maxLatency) {
		if (maxLatency < 20)
			return "low";
		else
			return "high";
	}

	private Dataset getDataset(String destination, double[] lat, double[] wanusage, double[] tasklength, double[] mobi,
			double[] destusage) {
		Dataset d = new Dataset("Sample1");

		// Add the attributes with Linguistic terms

		d.addAttribute(new Attribute("Destination", new String[] { "Cloud", "Edge", "Mist" }));
		d.addAttribute(new Attribute("Latency", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("WanUsage", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("TaskLength", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("Mob", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("DestUsage", new String[] { "High", "Medium", "Low" }));
		d.addAttribute(new Attribute("Offload", new String[] { "High", "Medium", "Low" }));
		double[][] columns = { getDestination(destination), lat, wanusage, tasklength, mobi, destusage, destusage };

		d.addRow(new Row(new Object[] { "Dummy", "Dummy", "Dummy", "Dummy", "Dummy", "Dummy", "Dummy" }, columns));

		return d;
	}

	/**
	 * generate the fuzzy decision tree used in each stage from the corresponding
	 * dataset
	 **/
	private FuzzyDecisionTree getDecisionTree(int stage) {
		Dataset d = new Dataset("Sample1");

		BufferedReader br = null;
		try {
			br = new BufferedReader(new FileReader("PureEdgeSim/MyWork/tree" + stage + ".txt"));
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}

		String line = "";
		String className = "";
		try {
			while ((line = br.readLine()) != null) {
				if (line.startsWith("#")) {
					String[] terms = line.substring(line.indexOf(":") + 1).split(" ");
					d.addAttribute(new Attribute(line.substring(1, line.indexOf(":")), terms));
				} else if (line.startsWith("@")) {
					String[] terms = line.substring(line.indexOf(":") + 1).split(" ");
					d.addAttribute(new Attribute(line.substring(1, line.indexOf(":")), terms));
					className = line.substring(1, line.indexOf(":"));
					d.setClassName(className);
				} else if (!line.startsWith("=Data=")) {
					String[] data = line.split(" ");
					Object[] crispRow = new Object[d.getAttributesCount()];
					double[][] fuzzyValues = new double[d.getAttributesCount()][];
					int k = 0;
					for (int i = 0; i < crispRow.length; i++) {
						crispRow[i] = "Dummy";
						// fuzzyValues[i] = getFuzzyValues(data[i]);
						fuzzyValues[i] = new double[d.getAttribute(i).getLinguisticTermsCount()];
						for (int j = 0; j < fuzzyValues[i].length; j++) {
							fuzzyValues[i][j] = Double.parseDouble(data[k++]);
						}
					}

					d.addRow(new Row(crispRow, fuzzyValues));

				}
			}
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}

		try {
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		PreferenceMeasure preferenceMeasure;
		LeafDeterminer leafDeterminer;
		FuzzyDecisionTree descisionTree;
		preferenceMeasure = new AmbiguityMeasure(0.5);
		leafDeterminer = new LeafDeterminerBase(0.9);

		descisionTree = new FuzzyDecisionTree(preferenceMeasure, leafDeterminer);
		if (stage == 1)
			root = descisionTree.buildTree(d);
		else
			root2 = descisionTree.buildTree(d);
		// Uncomment to print fuzzy rules
		/*
		 * String[] rulesArray = descisionTree.generateRules(root); String rules = "";
		 * for (String rule : rulesArray) rules += rule + "\r\n"; File file = new
		 * File("PureEdgeSim/MyWork/readable_rules_1"); try { DataOutputStream outstream
		 * = new DataOutputStream(new FileOutputStream(file, false));
		 * outstream.write(rules.getBytes()); outstream.close(); } catch (IOException e)
		 * { e.printStackTrace(); }
		 */
		return descisionTree;
	}

	private double[] getDestination(String destiantion) {
		if (destiantion.equals("cloud"))
			return new double[] { 1.0, 0.0, 0.0 };
		else if (destiantion.equals("edge"))
			return new double[] { 0.0, 1.0, 0.0 };
		else
			return new double[] { 0.0, 0.0, 1.0 };
	}

	private double[] getDesCPU(double d) {
		double l = 1, m = 2, h = 4;
		if (d >= h)
			return new double[] { 1.0, 0.0, 0.0 };
		if (d < l)
			return new double[] { 0.0, 0.0, 1.0 };
		if (d >= l && d < m)
			return new double[] { 0.0, (d - l) / (m - l), (m - d) / (m - l) };
		if (d > m && d < h)
			return new double[] { (d - m) / (h - m), (h - d) / (h - m), 0.0 };
		if (d == m)
			return new double[] { 0.0, 1.0, 0.0 };
		return null;
	}

	private String getDesCPUTerm(double mips) {
		if (mips >= 130000)
			return "high";
		else if (mips < 30000)
			return "low";
		else
			return "medium";
	}

	private String getDesCPUUsageTerm(double u) {
		if (u >= 4)
			return "high";
		else if (u <= 1)
			return "low";
		else
			return "medium";
	}

	private String getDestRemainTerm(DataCenter datacenter) {
		double e;
		if (datacenter.getEnergyModel().isBatteryPowered())
			e = datacenter.getEnergyModel().getBatteryLevelPercentage();
		else
			e = 100;
		if (e >= 75)
			return "high";
		else if (e <= 25)
			return "low";
		else
			return "medium";
	}

	private double[] getDesCPUusage(double d) {
		double l = 50;
		double h = 100;
		if (d <= 0)
			return new double[] { 0.0, 0.0, 1.0 };
		if (d >= h)
			return new double[] { 1.0, 0.0, 0.0 };
		if (d > 0 && d < l)
			return new double[] { 0.0, (d - 0) / (h - l), (l - d) / (h - l) };
		if (d > l && d < h)
			return new double[] { (d - l) / (h - l), (h - d) / (h - l), 0.0 };

		return new double[] { 0.0, 1.0, 0.0 };
	}

	private double[] getDestMobi(DataCenter datacenter, boolean mobile) {
		if (mobile && datacenter.getMobilityManager().isMobile())
			return new double[] { 1.0, 0.0, 0.0 };
		else if (mobile || datacenter.getMobilityManager().isMobile())
			return new double[] { 0.0, 1.0, 0.0 };
		return new double[] { 0.0, 0.0, 1.0 };
	}

	private String getDestMobiTerm(DataCenter datacenter, boolean mobile) {
		if (mobile && datacenter.getMobilityManager().isMobile())
			return "high";
		else if (mobile || datacenter.getMobilityManager().isMobile())
			return "medium";
		return "low";
	}

	private double[] getDestRemain(DataCenter datacenter) {
		double e;
		if (datacenter.getEnergyModel().isBatteryPowered())
			e = datacenter.getEnergyModel().getBatteryLevelPercentage();
		else
			e = 100;
		double l = 0;
		double h = 0;
		double m = 0;
		if (e <= 50) {
			h = 0;
			m = (e) / 50;
			l = (50 - e) / 50;
		} else {
			h = (e - 50) / 50;
			m = (100 - e) / 50;
			l = 0;
		}
		return new double[] { h, m, l };
	}

	private double[] getWanUsage(double d) {
		double l = 6, m = 10, h = 14;
		if (d >= h)
			return new double[] { 1.0, 0.0, 0.0 };
		if (d < l)
			return new double[] { 0.0, 0.0, 1.0 };
		if (d >= l && d < m)
			return new double[] { 0.0, (d - l) / (m - l), (m - d) / (m - l) };
		if (d > m && d < h)
			return new double[] { (d - m) / (h - m), (h - d) / (h - m), 0.0 };
		if (d == m)
			return new double[] { 0.0, 1.0, 0.0 };
		return null;
	}

	private String getWanUsageTerm(double d) {
		if (d <= 6)
			return "low";
		else if (d >= 14)
			return "high";
		else
			return "medium";
	}

	private double[] getTaskLength(double d) {
		if (d < 2000)
			return new double[] { 0.0, 0.0, 1.0 };
		if (d >= 18000)
			return new double[] { 1.0, 0.0, 0.0 };
		if (d <= 10000)
			return new double[] { 0.0, (d - 2000) / 8000, (10000 - d) / 8000 };
		return new double[] { (d - 10000) / 8000, (18000 - d) / 8000, 0.0 };
	}

	private String getTaskLengthTerm(long length) {
		if (length <= 6000)
			return "low";
		else if (length >= 14000)
			return "high";
		return "medium";
	}

	private double[] getLat(double maxLatency) {
		if (maxLatency < 20)
			return new double[] { 0.0, 0.0, 1.0 };
		return new double[] { 1.0, 0.0, 0.0 };
	}

	/** the first stage of the fuzzy logic-based algorithm **/
	private int FuzzyLogic(String[] architecture, Task task) {

		double vmUsage = 0;
		int count = 0;
		for (int i = 0; i < vmList.size(); i++) {
			if (((DataCenter) vmList.get(i).getHost().getDatacenter()).getType() != SimulationParameters.TYPES.CLOUD) {
				count++;
				vmUsage += ((DataCenter) vmList.get(i).getHost().getDatacenter()).getResources()
						.getCurrentCpuUtilization();

			}
		}
		// Send the input variables to the fuzzy inference system
		fis.setVariable("wan",
				SimulationParameters.WAN_BANDWIDTH / 1000 - simulationManager.getNetworkModel().getWanUtilization());
		fis.setVariable("tasklength", task.getLength());
		fis.setVariable("delay", task.getMaxLatency());
		fis.setVariable("vm", vmUsage * 10 / count);

		// Evaluate
		fis.evaluate();

		if (fis.getVariable("offload").defuzzify() > 50) {
			// The cloud has been selected.
			String[] architecture2 = { "Cloud" };
			return LatencyAndEnergyAware(architecture2, task);
		} else {
			// The cloud has not been selected. So choose between Edge and Mist in stage 2.
			String[] architecture2 = { "Edge", "Mist" };
			return FuzzyLogicStage2(architecture2, task);
		}
	}

	private int FuzzyLogicStage2(String[] architecture2, Task task) {
		double min = -1;
		int vm = -1;

		for (int i = 0; i < vmList.size(); i++) {
			if (offloadingIsPossible(task, vmList.get(i), architecture2)
					&& vmList.get(i).getStorage().getCapacity() > 0) {
				if (!task.getEdgeDevice().getMobilityManager().isMobile())
					fis2.setVariable("vm_local", 0);
				else
					fis2.setVariable("vm_local", 0);
				fis2.setVariable("vm", (1 - vmList.get(i).getCpuPercentUtilization()) * vmList.get(i).getMips() / 1000);
				fis2.evaluate();
				// if (b) {
				// fis2.chart();
				// b = false;
				// }
				if (min == -1 || min > fis2.getVariable("offload").defuzzify()) {
					min = fis2.getVariable("offload").defuzzify();
					vm = i;
				}
			}
		}
		return vm;
	}

	private int LatencyAndEnergyAware(String[] architecture, Task task) {
		int vm = -1;
		double min = -1;
		double new_min;// vm with minimum affected tasks;
		// get best vm for this task
		for (int i = 0; i < orchestrationHistory.size(); i++) {
			if (offloadingIsPossible(task, vmList.get(i), architecture)
					&& vmList.get(i).getStorage().getCapacity() > 0) {// &&
				// vmList.get(i).getStorage().getCapacity()>0
				double latency = 1;
				double energy = 1;
				if (((DataCenter) vmList.get(i).getHost().getDatacenter())
						.getType() == SimulationParameters.TYPES.CLOUD) {
					latency = 1.6;
					energy = 1.1;
				} else if (((DataCenter) vmList.get(i).getHost().getDatacenter())
						.getType() == SimulationParameters.TYPES.EDGE_DEVICE) {
					energy = 1.4;
				}
				new_min = (orchestrationHistory.get(i).size() + 1) * latency * energy * task.getLength()
						/ vmList.get(i).getMips();
				if (min == -1) { // if it is the first iteration
					min = new_min;
					// if this is the first time, set the first vm as the
					vm = i; // best one
				} else if (min > new_min) { // if this vm has more cpu mips and less waiting tasks
					// idle vm, no tasks are waiting
					min = new_min;
					vm = i;
				}
			}
		}
		// affect the tasks to the vm found
		return vm;
	}

	/** if the execution results of one of the offloaded task have been received **/
	@Override
	public void resultsReturned(Task task) {
		try {
			// if the used algorithm is called FDT (the fuzzy decision tree based algorithm)
			if (simulationManager.getScenario().getStringOrchAlgorithm().equals("FDT")) {
				// if the task has been successfully executed, consider it a reward. otherwise,
				// a punishement
				reinforcementRecieved(task, task.getStatus() == Status.SUCCESS, true);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	/**
	 * This function updates Q values and updates the decision tree. the Q table is
	 * saved in a file and then used to generate the new tree, in order to get the
	 * fuzzy rules, that are used in classification.
	 */
	public void reinforcementRecieved(Task task, boolean reinforcement, boolean modify) throws IOException {
		// get first rule from the array (the rule of stage 1)
		String ruleToUpdate = ((String[]) task.getMetaData())[0];
		double reward = reinforcement ? 1 : 0;
		if (((DataCenter) task.getVm().getHost().getDatacenter()).getMobilityManager().isMobile())
			reward /= 2;

		updateQTable(1, Q_Table, ruleToUpdate, reward);
		// if the edge has been selected in stage 2
		if (((DataCenter) task.getVm().getHost().getDatacenter()).getType() == TYPES.EDGE_DEVICE) {
			// get the second rule from the array (the rule of stage 2)
			ruleToUpdate = ((String[]) task.getMetaData())[1];
			updateQTable(2, Q_Table2, ruleToUpdate, reward);
		}
	}

	/**
	 * Here we update the Q value of a state-action pair according to the received
	 * reward
	 **/
	private void updateQTable(int stage, Vector<Q_table_row> q_Table, String ruleToUpdate, double reinforcement) {
		// browse the Q_table, in order to update the Q value of that rule
		boolean found = false;
		double Q_value = reinforcement;
		for (int i = 0; i < q_Table.size(); i++) {
			if (q_Table.get(i).getRule().equals(ruleToUpdate)) {
				found = true;
				Q_value = q_Table.get(i).getQ_value();
				// update Q-value
				q_Table.get(i).incrementNumberOfReinforcements();

				// set the threshold as 100 in order to deal with non-stationary Multi-Armed
				// Bandit problem
				double k = Math.min(q_Table.get(i).getNumberOfReinforcements(), 100);
				Q_value += 1 / k * (reinforcement - Q_value);
				q_Table.get(i).setQ_value(Q_value);
				break;
			}
		}
		if (!found) {
			Q_table_row row = new Q_table_row(ruleToUpdate, 1, 1);
			q_Table.add(row);
		}

		/*
		 * if (reinforcement == 1) System.out.println(ruleToUpdate); else
		 * System.err.println(ruleToUpdate + "   " + Q_value);
		 */
		try {
			UpdateFuzzyDecisionTree(q_Table, stage);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void UpdateFuzzyDecisionTree(Vector<Q_table_row> q_Table, int stage) throws IOException {
		String rules = "";
		if (stage == 1) {
			rules += "#Destination:Cloud Edge Mist\r\n" + "#Latency:High Medium Low\r\n"
					+ "#WanUsage:High Medium Low\r\n" + "#TaskLength:High Medium Low\r\n" + "#Mob:High Medium Low\r\n"
					+ "#DestUsage:High Medium Low\r\n" + "@Offload:High Medium Low\r\n" + "=Data=";
		} else {
			rules += "#DestCPU:High Medium Low\r\n" + "#TaskLength:High Medium Low\r\n" + "#DestRem:High Medium Low\r\n"
					+ "#DestMob:High Medium Low\r\n" + "@Offload:High Medium Low\r\n" + "=Data=";
		}
		for (int i = 0; i < q_Table.size(); i++) {
			rules += "\r\n" + convertRule(q_Table.get(i).getRule()) + " " + getClass(q_Table, q_Table.get(i), stage);
		}

		File file = new File("PureEdgeSim/MyWork/tree" + stage + ".txt");
		DataOutputStream outstream = new DataOutputStream(new FileOutputStream(file, false));
		outstream.write(rules.getBytes());
		outstream.close();

	}

	private String convertRule(String rule) {
		return rule.replace("cloud", "1.0 0.0 0.0").replace("edge", "0.0 1.0 0.0").replace("mist", "0.0 0.0 1.0")
				.replace("high", "1.0 0.0 0.0").replace("medium", "0.0 1.0 0.0").replace("low", "0.0 0.0 1.0")
				.replace(",", " ");
	}

	private String getClass(Vector<Q_table_row> q_Table, Q_table_row row, int stage) {
		if (stage == 2)
			return fuzzify(row.getQ_value());

		double max = 0;
		for (int i = 0; i < q_Table.size(); i++) {
			if (q_Table.get(i).getQ_value() > max && compare(q_Table.get(i).getRule(), row.getRule()))
				max = q_Table.get(i).getQ_value();
		}
		double new_Q_value = 0;
		if (max != 0)
			new_Q_value = 1 - ((max - row.getQ_value()) / max);

		return fuzzify(new_Q_value);
	}

	private boolean compare(String rule, String rule2) {
		rule = rule.replace("edge", "cloud");
		rule = rule.replace("mist", "cloud");
		rule2 = rule2.replace("edge", "cloud");
		rule2 = rule2.replace("mist", "cloud");
		return rule.equals(rule2);
	}

	private String fuzzify(double new_Q_value) {
		double high = new_Q_value >= 0.5 ? (new_Q_value - 0.5) / 0.5 : 0;
		double medium = new_Q_value >= 0.5 ? (1 - new_Q_value) / 0.5 : new_Q_value / 0.5;
		double low = new_Q_value <= 0.5 ? (0.5 - new_Q_value) / 0.5 : 0;
		return high + " " + medium + " " + low;
	}

}
