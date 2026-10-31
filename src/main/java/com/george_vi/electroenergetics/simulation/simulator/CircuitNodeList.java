package com.george_vi.electroenergetics.simulation.simulator;

import com.george_vi.electroenergetics.simulation.electrical_properties.ElectricalProperties;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.Arrays;

/**
 * The primary objective of this class is to hold node and circuit graph data.
 * <br>
 * It's done as flat arrays to help with cache locality during iterations such as DFS
 * (SOA is better than AOS)
 */
public class CircuitNodeList {
    private int totalNodes = 0;
    private int capacity = 0;

    // Node Data:
    private CircuitNode[] nodes;
    private double[] nodeGroundConductance;
    private int[] nodeGroundPriority;
    private byte[] nodeDegree;
    //

    // Node Adjacency Data:
    /**
     * Neighbors IDs of nodes with up to 4 neighbors are stored here
     * <br>
     * If the first integer is equal to -1, the adjacency of this node is stored in the
     * CircuitNode object (there are more than four neighbors).
     * <br>
     * Each neighbor ID is one higher! That is to make 0 (the default) represent an unused slot / termination
     * of neighbor sequence
     * <br>
     * Access the adjacent node index like: {@code adjacentNodes[id * 4 + neighborIndex] - 1}
     */
    private int[] adjacentNodes;
    /**
     * Neighbor resistances of nodes with up to 4 neighbors are stored here
     * <br>
     * If the value is equal to -1,
     * the adjacency of this node is stored in {@code adjacentProperties}
     * <br>
     * Access the adjacent node resistance like: {@code adjacentResistances[id * 4 + neighborIndex]}
     */
    private double[] adjacentResistances;
    /**
     * Neighbor resistances of nodes with up to 4 neighbors are stored here
     * <br>
     * If the value is equal to null, the adjacency of this node is stored in {@code adjacentResistance}
     * <br>
     * Access the adjacent value like: {@code adjacentProperties[id * 4 + neighborIndex]}
     */
    private ElectricalProperties[] adjacentProperties;
    //

    public CircuitNodeList() {
        this(16);
    }

    public CircuitNodeList(int size) {
        initToSize(size);
    }

    private void initToSize(int size) {
        nodes = new CircuitNode[size];
        nodeGroundPriority = new int[size];
        nodeGroundConductance = new double[size];
        nodeDegree = new byte[size];
        adjacentNodes = new int[size * 4];
        adjacentResistances = new double[size * 4];
        adjacentProperties = new ElectricalProperties[size * 4];
        capacity = size;
    }

    private void growToFit(int totalNodes) {
        nodes = Arrays.copyOf(nodes, totalNodes);
        nodeGroundPriority = Arrays.copyOf(nodeGroundPriority, totalNodes);
        nodeGroundConductance = Arrays.copyOf(nodeGroundConductance, totalNodes);
        nodeDegree = Arrays.copyOf(nodeDegree, totalNodes);
        adjacentNodes = Arrays.copyOf(adjacentNodes, totalNodes * 4);
        adjacentResistances = Arrays.copyOf(adjacentResistances, totalNodes * 4);
        adjacentProperties = Arrays.copyOf(adjacentProperties, totalNodes * 4);
        capacity = totalNodes;
    }

    public void clear() {
        Arrays.fill(nodes, null);
        Arrays.fill(nodeGroundPriority, 0);
        Arrays.fill(nodeGroundConductance, 0);
        Arrays.fill(nodeDegree, (byte) 0);
        Arrays.fill(adjacentNodes, 0);
        Arrays.fill(adjacentResistances, 0);
        Arrays.fill(adjacentProperties, null);
        totalNodes = 0;
    }

    public int addNode() {
        int id = totalNodes++;
        if (id >= capacity)
            growToFit(capacity * 2);
        return id;
    }

    public void setGroundConductance(int node, double conductance) {
        checkID(node);

        nodeGroundConductance[node] = conductance;
    }

    public void setGroundPriority(int node, int priority) {
        checkID(node);

        nodeGroundPriority[node] = priority;
    }

    public void connect(int node1, int node2, ElectricalProperties properties) {
        checkID(node1);
        checkID(node2);

        updateAdjacency(node1, node2, properties);
        updateAdjacency(node2, node1, properties.invert());
    }

    private void updateAdjacency(int node1, int node2, ElectricalProperties properties) {
        boolean storedExternally = false;
        nodeDegree[node1]++;
        for (int i = 0; i < 4; i++) {
            int adj1 = adjacentNodes[node1 * 4 + i];
            if (adj1 == -1) {
                storedExternally = true;
                break;
            }
            if (adj1 == 0) {
                adjacentNodes[node1 * 4 + i] = node2 + 1;
                if (properties.isSimpleResistor())
                    adjacentResistances[node1 * 4 + i] = properties.resistance();
                else {
                    adjacentResistances[node1 * 4 + i] = -1;
                    adjacentProperties[node1 * 4 + i] = properties;
                }
                return;
            }
        }
        if (nodes[node1] == null)
            nodes[node1] = new CircuitNode(node1);
        CircuitNode node = nodes[node1];

        if (storedExternally) {
            node.adjacency.put(node2, properties);
        } else {
            // couldn't find space
            for (int i = 0; i < 4; i++) {
                int adj = adjacentNodes[node1 * 4 + i];
                if (adj == 0)
                    break;
                ElectricalProperties p = adjacentProperties[node1 * 4 + i];
                node.adjacency.put(adj - 1, p == null ?
                        ElectricalProperties.resistor(adjacentResistances[node1 * 4 + i]) :
                        p);
            }
            adjacentNodes[node1 * 4] = -1;
            node.adjacency.put(node2, properties);
        }
    }

    private void checkID(int id) {
        if (id < 0 || id >= totalNodes)
            throw new IndexOutOfBoundsException(totalNodes);
    }

    public int totalNodes() {
        return totalNodes;
    }

    public Int2ObjectMap<ElectricalProperties> getNeighbors(int nodeID) {
        checkID(nodeID);
        Int2ObjectMap<ElectricalProperties> out = new Int2ObjectOpenHashMap<>(nodeDegree[nodeID]);
        for (int i = 0; i < 4; i++) {
            int adj1 = adjacentNodes[nodeID * 4 + i];
            if (adj1 == -1) {
                // stored in the other object
                // it's safe to assume i == 0
                return nodes[nodeID].adjacency;
            }

            if (adj1 == 0)
                break;

            double resistance = adjacentResistances[nodeID * 4 + i];
            if (resistance == -1) {
                out.put(adj1 - 1, adjacentProperties[nodeID * 4 + i]);
            } else {
                out.put(adj1 - 1, ElectricalProperties.resistor(resistance));
            }
        }
        return out;
    }

    public double getGroundConductance(int nodeID) {
        checkID(nodeID);
        return nodeGroundConductance[nodeID];
    }

    public static class CircuitNode {
        final int id;
        final Int2ObjectMap<ElectricalProperties> adjacency;

        public CircuitNode(int id) {
            this.id = id;
            adjacency = new Int2ObjectOpenHashMap<>();
        }
    }
}
