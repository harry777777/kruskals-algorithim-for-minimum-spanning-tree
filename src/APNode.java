import java.util.ArrayList;
import java.util.HashSet;
/**
 * Node used for helping the iterative articulation point search
 * contains a field for Depth,reachback, current node and  parent node field, 
 *
 */
public class APNode {
	public Node APparent;
	public Node APcurrentNode;
	public double APdepth;
	public double APreachBack;
	public ArrayList<Node> APchildren;
	public HashSet<Node> APneighbours;

	
	public APNode(Node n){
		this.APparent=null;
		this.APcurrentNode = n;
		this.APdepth =Double.POSITIVE_INFINITY;
		this.APreachBack = this.APdepth;
		this.APchildren = new ArrayList<Node>();
		this.APneighbours = new HashSet<Node>();
	}
	
	
	
}


