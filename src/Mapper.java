import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Stack;

/**
 * This is the main class for the mapping program. It extends the GUI abstract
 * class and implements all the methods necessary, as well as having a main
 * function.
 * 
 * @author tony
 */
public class Mapper extends GUI {
	public static final Color NODE_COLOUR = new Color(77, 113, 255);
	public static final Color SEGMENT_COLOUR = new Color(130, 130, 130);
	public static final Color HIGHLIGHT_COLOUR = new Color(255, 219, 77);

	// these two constants define the size of the node squares at different zoom
	// levels; the equation used is node size = NODE_INTERCEPT + NODE_GRADIENT *
	// log(scale)
	public static final int NODE_INTERCEPT = 1;
	public static final double NODE_GRADIENT = 0.8;

	// defines how much you move per button press, and is dependent on scale.
	public static final double MOVE_AMOUNT = 100;
	// defines how much you zoom in/out per button press, and the maximum and
	// minimum zoom levels.
	public static final double ZOOM_FACTOR = 1.3;
	public static final double MIN_ZOOM = 1, MAX_ZOOM = 200;

	// how far away from a node you can click before it isn't counted.
	public static final double MAX_CLICKED_DISTANCE = 0.15;

	// these two define the 'view' of the program, ie. where you're looking and
	// how zoomed in you are.
	private Location origin;
	private double scale;

	// our data structures.
	private Graph graph;
	private HashSet<Node> ArtPoints = new HashSet<Node>();
	private HashSet<Node> toVisit;// used in articulation points search
	private HashSet<Node> Forest; // used for Kruskals minimum spanning tree
	private PriorityQueue<Segment> kruskalFringe;
	private ArrayList<Segment> spanningTree;
	private  double sum;

	@Override
	protected void redraw(Graphics g) {
		if (graph != null)
			graph.draw(g, getDrawingAreaDimension(), origin, scale);
	}

	class segmentComparator implements Comparator<Segment> {

		public int compare(Segment s1, Segment s2) {
			if (s1.length < s2.length) {
				return -1;
			} else if (s1.length > s2.length) {
				return 1;

			}
			return 0;

		}
	}

	@Override
	protected void onClick(MouseEvent e) {
		Location clicked = Location.newFromPoint(e.getPoint(), origin, scale);
		// find the closest node.
		double bestDist = Double.MAX_VALUE;
		Node closest = null;
		graph.selected = null; // set selected node to null before search
		for (Node node : graph.nodes.values()) {
			double distance = clicked.distance(node.location);
			if (distance < bestDist) {
				bestDist = distance;
				closest = node;
			}
		}

		// if it's close enough, highlight it and show some information.
		if (clicked.distance(closest.location) < MAX_CLICKED_DISTANCE) {
			graph.setHighlight(closest);
			graph.selected = closest; // set a selected node for use in art point search
			getTextOutputArea().setText(closest.toString());
		}
	}

	@Override
	protected void onSearch() {
		this.ArtPoints = new HashSet<Node>();
		this.toVisit = new HashSet<Node>();
		this.Forest = new HashSet<Node>();
		this.kruskalFringe = new PriorityQueue<Segment>(new segmentComparator());
		this.spanningTree = new ArrayList<Segment>();
		this.sum=0;
		for (Segment s : graph.segments) {
			kruskalFringe.offer(s);
		}
		for (Entry<Integer, Node> Entry : graph.nodes.entrySet()) {
			toVisit.add(Entry.getValue());
		}
		InitialiseNodes();
		KruskalsTree();
		for (Entry<Integer, Node> Entry : graph.nodes.entrySet()) {
			if (toVisit.contains(Entry.getValue()))
				findArticulationPoints(Entry.getValue());
		}
		System.out.println(this.ArtPoints.size() + "size  of  articulation Points list");

	}

	@Override
	protected void onMove(Move m) {
		if (m == GUI.Move.NORTH) {
			origin = origin.moveBy(0, MOVE_AMOUNT / scale);
		} else if (m == GUI.Move.SOUTH) {
			origin = origin.moveBy(0, -MOVE_AMOUNT / scale);
		} else if (m == GUI.Move.EAST) {
			origin = origin.moveBy(MOVE_AMOUNT / scale, 0);
		} else if (m == GUI.Move.WEST) {
			origin = origin.moveBy(-MOVE_AMOUNT / scale, 0);
		} else if (m == GUI.Move.ZOOM_IN) {
			if (scale < MAX_ZOOM) {
				// yes, this does allow you to go slightly over/under the
				// max/min scale, but it means that we always zoom exactly to
				// the centre.
				scaleOrigin(true);
				scale *= ZOOM_FACTOR;
			}
		} else if (m == GUI.Move.ZOOM_OUT) {
			if (scale > MIN_ZOOM) {
				scaleOrigin(false);
				scale /= ZOOM_FACTOR;
			}
		}
	}

	@Override
	protected void onLoad(File nodes, File roads, File segments, File polygons) {
		graph = new Graph(nodes, roads, segments, polygons);
		origin = new Location(-250, 250); // close enough
		scale = 1;
	}

	/**
	 * This method does the nasty logic of making sure we always zoom into/out of
	 * the centre of the screen. It assumes that scale has just been updated to be
	 * either scale * ZOOM_FACTOR (zooming in) or scale / ZOOM_FACTOR (zooming out).
	 * The passed boolean should correspond to this, ie. be true if the scale was
	 * just increased.
	 */
	private void scaleOrigin(boolean zoomIn) {
		Dimension area = getDrawingAreaDimension();
		double zoom = zoomIn ? 1 / ZOOM_FACTOR : ZOOM_FACTOR;

		int dx = (int) ((area.width - (area.width * zoom)) / 2);
		int dy = (int) ((area.height - (area.height * zoom)) / 2);

		origin = Location.newFromPoint(new Point(dx, dy), origin, scale);
	}

	public static void main(String[] args) {
		new Mapper();
	}

	/**
	 * iterate every node of the graph, resets and sets all values in fields used
	 * for the searches also populates the forest for kruskals search and
	 */
	public void InitialiseNodes() {
		for (Entry<Integer, Node> Entry : graph.nodes.entrySet()) {
			addNeighbors(Entry.getValue());
			Entry.getValue().depth = Double.POSITIVE_INFINITY;
			Entry.getValue().reachback = 0;
			Entry.getValue().children.clear();
			Entry.getValue().kruskalParent = Entry.getValue();
			Entry.getValue().treeDepth = 0;
			this.Forest.add(Entry.getValue());

		}

	}

	/**
	 * takes a Node object and loops through all edges adding all neighbouring nodes
	 * into the nodes neighbour set;
	 */

	public static void addNeighbors(Node n) {
		for (Segment s : n.segments) {
			if (s.end != n) {
				n.getNeighbors().add(s.end);
			}
			if (s.start != n) {
				n.getNeighbors().add(s.start);

			}

		}
	}

	/**
	 * takes the selected Node, then finds all connected articulation points using
	 * the selected node as the root
	 * 
	 * @param root
	 * 
	 */
	public void findArticulationPoints(Node root) {
		root.depth = 0;
		root.numSubTrees = 0; // initialise roots depth and subtree count to 0;
		for (Node n : root.neighbours) {
			if (n.depth == Double.POSITIVE_INFINITY) { // for all neighbours of root, if they are unvisited,
				iterateArtPoints(n, 1, root); // iterate over node and increment the subtree count of root
				root.numSubTrees++;
			}
			if (root.numSubTrees > 1) {
				this.ArtPoints.add(root); // if there are more than one subtree for root it is an AP, so add root to
											// AP's
			}
		}
	}

	/**
	 * iterates over nodes in the graph, finding all articulation points that are
	 * connected to a root node
	 * 
	 * @param current
	 * @param depth
	 * @param parent
	 */

	public void iterateArtPoints(Node current, double depth, Node parent) {
		Stack<APNode> fringe = new Stack<APNode>();
		APNode first = new APNode(current); // intitialise the first APnode object and the fringe,
		first.APdepth = depth; // set the depth of the first APnode root depth +1 =1
		first.APparent = parent; // set the parent to the root
		fringe.push(first); // add to fringe and begin the looping
		while (fringe.size() > 0) {
			APNode APn = fringe.peek(); // peek an APNode out the stack
			Node n = APn.APcurrentNode; // n is the current node of the current APnode
			if (n.depth == Double.POSITIVE_INFINITY) { // if most recently peeked nodes current node is unvisited
				n.depth = APn.APdepth; // set the depth of the current Node to that of the APnode
				n.reachback = APn.APdepth; // set the reachBack of the current Node to match the APnodes depth
				for (Node neigh : n.neighbours) {
					if (!neigh.equals(APn.APparent) && !n.children.contains(neigh)) { // add all of the current nodes
																						// neighbors to its children
																						// list excluding parents and
																						// with a check for duplicates
						n.children.add(neigh);
					}
				}
			} else if (n.children.size() > 0) { // if most recently peeked node is visited and has children

				Node c = n.children.get(0); // retreive a child node from current nodes child list and remove it from
											// child list
				n.children.remove(c);
				if (c.depth < Double.POSITIVE_INFINITY) { // if the child has been visited,
					n.reachback = Math.min(n.reachback, c.depth); // set the reach back of node N to the smaller
																	// value of N's reach back or the childs depth

				} else { // the child has not been visited
					APNode APchild = new APNode(c); // create a new APNode for the fringe with the child,
					APchild.APdepth = APn.APdepth + 1; // set the APnodes parent field to n and increment depth+1
					APchild.APparent = n;
					fringe.push(APchild); // add child APnode to the stack
				}

			} else { // node is visited but does not have children

				if (!n.equals(current)) { // if n is not the first node
					APn.APparent.reachback = Math.min(APn.APparent.reachback, n.reachback); // set the value of the
																							// parent node in APnode to
																							// the min reachback of it
																							// or the current node

					if (n.reachback >= APn.APparent.depth) { // if the reachback of n is >= to the depth of the parent
						this.ArtPoints.add(APn.APparent); // add parent into art point set
						APn.APparent.highlight = true;// for highlighting on map
					}

				}
				fringe.remove(APn); // node fully processed, remove from stack
				toVisit.remove(n); // remove from toVisit, list of nodes to make sure it works on a disconected
									// graph
			}

		}
		// System.out.println("NUM OF AP'S " + ArtPoints.size() + " /n");
	}

	/**
	 * builds minimum spanning trees for all graph components,
	 * 
	 */
	public void KruskalsTree() {
		while (Forest.size() > 1 && !kruskalFringe.isEmpty()) {
			Segment s = kruskalFringe.poll();
			if (find(s.start) != find(s.end)) {
				union(s.start, s.end, s);

			}
		}
		System.out.println(spanningTree.size());
		System.out.println(this.sum+"sum of  lengths");
		System.out.println(Forest.size()+" number  of  spannning trees");
	}

	/**
	 * find method used in kruskals algorithim, when called on a node will recurse
	 * up its set of nodes until it has found the root of the tree then return the
	 * node
	 * 
	 * @param n
	 * @return Node
	 */
	public Node find(Node n) {
		if (n.kruskalParent == n) {
			return n; // n is root

		} else {
			Node root = find(n.kruskalParent); // recurse up tree till we find the root
			return root;
		}

	}

	public void union(Node x, Node y, Segment s) {
		Node xRoot = find(x);
		Node yRoot = find(y);
		if (xRoot.equals(yRoot)) {
			return; // if both roots are the same both nodes are part of the same tree
		} else {
			spanningTree.add(s);
			s.highlight = true;
			this.sum = sum+s.length;
			if (xRoot.treeDepth < yRoot.treeDepth) {
				xRoot.kruskalParent = yRoot;								
				Forest.remove(xRoot); // merge smaller trees into bigger
			} else {
				yRoot.kruskalParent = xRoot;
				Forest.remove(yRoot);
				if (xRoot.treeDepth == yRoot.treeDepth) {
					xRoot.treeDepth++; // must increment depth of xRoot if the trees have the same depth
				}

			}

		}
	}
}

// code for COMP261 assignments