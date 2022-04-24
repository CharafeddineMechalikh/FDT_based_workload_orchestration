package MyWork;
 
import com.mechalikh.pureedgesim.simulationmanager.Simulation;

public class Main{
	// Below is the path for the settings folder of this example
	private static String settingsPath = "PureEdgeSim/MyWork/settings/";

	// The custom output folder is
	private static String outputPath = "PureEdgeSim/MyWork/output/";

	public static void main(String[] args) {
		
		Simulation sim= new Simulation();
		// changing the default output folder
		sim.setCustomOutputFolder(outputPath);

		// changing the simulation settings folder
		sim.setCustomSettingsFolder(settingsPath);

		// Load the custom devices class
		sim.setCustomEdgeDataCenters(CustomComputingNode.class); 
		
	    sim.setCustomEdgeOrchestrator(CustomOrchestrator.class);
		// Launch the simulation
		sim.launchSimulation();
	}

}
