package MyWork;

public class Q_table_row {
	private String rule = "";
	private double numberOfReinforcements = 0;
	private double Q_value = 0;

	public Q_table_row(String rule, double numberOfReinforcements, double Q_value) {
		this.rule = rule;
		this.numberOfReinforcements = numberOfReinforcements;
		this.Q_value = Q_value;
	}

	public Q_table_row(String rule, String numberOfReinforcements, String Q_value) {
		this.rule = rule;
		this.numberOfReinforcements = Double.parseDouble(numberOfReinforcements);
		this.Q_value = Double.parseDouble(Q_value);
	}

	public String getRule() {
		return rule;
	}

	public void setRule(String rule) {
		this.rule = rule;
	}

	public double getNumberOfReinforcements() {
		return numberOfReinforcements;
	}

	public void incrementNumberOfReinforcements() {
		this.numberOfReinforcements++;
	}

	public double getQ_value() {
		return Q_value;
	}

	public void setQ_value(double q_value) {
		Q_value = q_value;
	}

}
