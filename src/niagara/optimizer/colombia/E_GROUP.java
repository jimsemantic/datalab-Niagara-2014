package niagara.optimizer.colombia;

/***
 * ============================================================ E_GROUP - Task
 * to Explore the Group
 * ============================================================ Some rules
 * require that their inputs contain particular (target) operators. For example,
 * the associativity rule requires that one input contain a join. The E_GROUP
 * task explores a group by creating all target operators that could belong to
 * the group, e.g., fire whatever rules are necessary to create all joins that
 * could belong to the group.
 * 
 * The simplest implementation of this task is to generate all logical
 * multiexpressions in the group.
 * 
 * More sophisticated implementations would fire only those rules which might
 * generate the target operator. But it is hard to tell what those rules are
 * (note that it may require a sequence of rules to get to the target).
 * Furthermore, on a second E_GROUP task for a second target it may be difficult
 * to use the results of the first visit intelligently.
 * 
 * Because we are using the simple implementation, we do not need an E_EXPR
 * task. Instead we will use the O_GROUP task but ensure that it fires only
 * transformation rules.
 * 
 * If we are lucky, groups will never need to be explored: physical rules are
 * fired first, and the firing of a physical rule will cause all inputs to be
 * optimized, therefore explored. This may not work if we are using pruning: we
 * might skip physical rule firings because of pruning, then need to explore.
 * For now we will put a flag in E_GROUP to catch when it does not work.
 */

public class E_GROUP extends Task {

	private Group group; // Group to be explored
	// private boolean last; // is it the last task in this group
	private Cost epsBound;

	// if global eps pruning is on, this is the eps bound for eps pruning
	// else it is zero

	// Task to explore a group
	E_GROUP(SSP ssp, Group group, Context context, boolean last, Cost bound) {
		super(ssp, context);
		this.group = group;
		// this.last = last;
		this.epsBound = bound;
	}

	E_GROUP(SSP ssp, Group group, Context context) {
		this(ssp, group, context, false, null);
	}

	public void perform() {
		if (group.isOptimized() || group.isExplored()) {
			// See discussion in E_GROUP class declaration
			return;
		}

		assert !group.isExploring();

		// the group will be explored, let other tasks don't do it again
		group.setExploring(true);

		// mark the group not explored since we will begin exploration
		group.setExplored(false);

		MExpr LogMExpr = group.getFirstLogMExpr();

		// only need to E_EXPR the first log expr,
		// because it will generate all logical exprs by applying appropriate
		// rules
		// it won't generate dups because rule bit vector
		// PTRACE0("pushing O_EXPR exploring " + LogMExpr.Dump());
		// this logical mexpr will be the last optimized one, mark it as the
		// last task for this group
		if (ssp.GlobepsPruning) {
			Cost eps_bound = new Cost(epsBound);
			ssp.addTask(new O_EXPR(ssp, LogMExpr, true, context, true,
					eps_bound));
		} else
			ssp.addTask(new O_EXPR(ssp, LogMExpr, true, context, true));
	}

	public String toString() {
		return "Exploring " + group;
	}
}
