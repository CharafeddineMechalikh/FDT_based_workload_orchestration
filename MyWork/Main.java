package MyWork;

import com.mechalikh.pureedgesim.MainApplication;

public class Main extends MainApplication {
	// Below is the path for the settings folder of this example
	private static String settingsPath = "PureEdgeSim/MyWork/settings/";

	// The custom output folder is
	private static String outputPath = "PureEdgeSim/MyWork/output/";

	public Main(int fromIteration, int step_) {
		super(fromIteration, step_);
		// TODO Auto-generated constructor stub
	}

	public static void main(String[] args) {

		// changing the default output folder
		setCustomOutputFolder(outputPath);

		// changing the simulation settings folder
		setCustomSettingsFolder(settingsPath);
		
        // load the custom tasks orchestrator and algorithms
		Main.setCustomEdgeOrchestrator(CustomOrchestrator.class);
		// Launch the simulation
		Main.launchSimulation();
	}

}
