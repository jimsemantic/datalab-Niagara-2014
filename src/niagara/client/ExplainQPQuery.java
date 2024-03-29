package niagara.client;

public class ExplainQPQuery extends Query {
	String text;

	public ExplainQPQuery(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public String getCommand() {
		return "explain_qp_query";
	}

	public String getDescription() {
		return "ExplainQP";
	}

	public int getType() {
		return QueryType.EXPLAIN;
	}
}
