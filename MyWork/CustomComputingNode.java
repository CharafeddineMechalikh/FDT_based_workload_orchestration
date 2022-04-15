package MyWork;

import com.mechalikh.pureedgesim.datacentersmanager.DefaultComputingNode;
import com.mechalikh.pureedgesim.simulationmanager.SimulationManager;

public class CustomComputingNode extends DefaultComputingNode {
	private Object metaData;

	public CustomComputingNode(SimulationManager simulationManager, double mipsPerCore, long numberOfCPUCores,
			long storage) {
		super(simulationManager, mipsPerCore, numberOfCPUCores, storage);
	}


	public Object getMetaData() {
		return metaData;
	}

	public void setMetaData(Object metaData) {
		this.metaData = metaData;
	}


}
