/*
*    Copyright 2013 University of Southern California
*
*  Licensed under the Apache License, Version 2.0 (the "License");
*  you may not use this file except in compliance with the License. 
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*/

package edu.usc.goffish.gofs.itest;

import java.io.*;
import java.nio.file.*;
import java.util.*;

import org.apache.commons.io.*;

import junit.framework.*;

import edu.usc.goffish.gofs.*;
import edu.usc.goffish.gofs.formats.gml.*;
import edu.usc.goffish.gofs.partition.components.*;
import edu.usc.goffish.gofs.slice.*;
import edu.usc.goffish.gofs.util.*;

/**
 * Integration test to assert the Graph Template and Instance values using Kryo Serialization. This class looks as similar
 * as PartitionTest, but it is separated to maintain test isolation and avoid dependencies.
 */
public class KryoSerializerIntegrationTest extends TestCase implements IntegrationTest {
	private static final String TemplateFile = "Template.gml";
	private static final String[] InstanceFiles = new String[]{ "Instance_0.gml", "Instance_1.gml" };

	public void testKryoSerialization() throws IOException {
		Path sliceDir = Files.createTempDirectory("slices");
		try {
			GMLPartition gmlPartition = GMLPartition.parseGML(1, new WCCComponentizer(), ClassLoader.getSystemResourceAsStream(TemplateFile), new GMLFileIterable(InstanceFiles));
			
			// Write the partition
			UUID partitionUUID = UUID.randomUUID();
			ISliceManager sliceManager = SliceManager.create(partitionUUID, new KryoSliceSerializer(), new FileStorageManager(sliceDir));
			sliceManager.writePartition(gmlPartition, 1, gmlPartition.size());
	
			// Read the partition
			sliceManager = SliceManager.create(partitionUUID, new KryoSliceSerializer(), new FileStorageManager(sliceDir));
			IPartition actualPartition = sliceManager.readPartition();
			assertNotNull(actualPartition);
	
			// Test the partition
			comparePartitionTemplates(gmlPartition, actualPartition);
			comparePartitionInstances(actualPartition);
			
		} finally {
			FileUtils.deleteQuietly(sliceDir.toFile());
		}
	}

	protected void comparePartitionTemplates(IPartition expected, IPartition actual) {
		assertEquals(expected.getId(), actual.getId());
		assertEquals(expected.isDirected(), actual.isDirected());
		assertEquals(expected.size(), actual.size());
		
		for (ISubgraph expectedSubgraph : expected) {
			ISubgraph actualSubgraph = actual.getSubgraph(expectedSubgraph.getId());
			assertNotNull(actualSubgraph);
			
			List<ITemplateVertex> expectedRemoteVertices = IterableUtils.toList(expectedSubgraph.remoteVertices());
			List<ITemplateVertex> actualRemoteVertices = IterableUtils.toList(actualSubgraph.remoteVertices());
			assertTrue(expectedRemoteVertices.containsAll(actualRemoteVertices));
			assertEquals(expectedRemoteVertices.size(), actualRemoteVertices.size());
			
			for (Property expectedProperty : expectedSubgraph.getVertexProperties()){
				Property actualProperty = actualSubgraph.getVertexProperties().getProperty(expectedProperty.getName());
				assertNotNull(actualProperty);
				assertEquals(expectedProperty.isStatic(), actualProperty.isStatic());
				assertEquals(expectedProperty.getType(), actualProperty.getType());
				assertEquals(expectedProperty.getDefaults().size(), actualProperty.getDefaults().size());
				for (long id : expectedProperty.getDefaults().keySet()) {
					assertTrue(actualProperty.getDefaults().containsKey(id));
					assertEquals(expectedProperty.getDefaults().get(id), actualProperty.getDefaults().get(id));
				}
			}
			for (Property expectedProperty : expectedSubgraph.getEdgeProperties()){
				Property actualProperty = actualSubgraph.getEdgeProperties().getProperty(expectedProperty.getName());
				assertNotNull(actualProperty);
				assertEquals(expectedProperty.isStatic(), actualProperty.isStatic());
				assertEquals(expectedProperty.getType(), actualProperty.getType());
				assertEquals(expectedProperty.getDefaults().size(), actualProperty.getDefaults().size());
				for (long id : expectedProperty.getDefaults().keySet()) {
					assertTrue(actualProperty.getDefaults().containsKey(id));
					assertEquals(expectedProperty.getDefaults().get(id), actualProperty.getDefaults().get(id));
				}
			}
			
			ISubgraphTemplate<? extends ITemplateVertex, ? extends ITemplateEdge> expectedTemplate = expectedSubgraph.getTemplate();
			ISubgraphTemplate<? extends ITemplateVertex, ? extends ITemplateEdge> actualTemplate = actualSubgraph.getTemplate();
			assertEquals(expectedTemplate.numVertices(), actualTemplate.numVertices());
			assertEquals(expectedTemplate.numEdges(), actualTemplate.numEdges());
			assertEquals(expectedTemplate.isDirected(), actualTemplate.isDirected());
			
			for (ITemplateVertex vertex : expectedTemplate.vertices()) {
				assertTrue(actualTemplate.containsVertex(vertex.getId()));
				assertEquals(expected.getSubgraphForVertex(vertex.getId()).getId(), actual.getSubgraphForVertex(vertex.getId()).getId());
			}
			for (ITemplateEdge expectedEdge : expectedTemplate.edges()) {
				ITemplateVertex actualSource = actualTemplate.getVertex(expectedEdge.getSource().getId());
				ITemplateVertex actualSink = actualTemplate.getVertex(expectedEdge.getSink().getId());
				assertTrue(actualTemplate.containsEdge(expectedEdge.getId(), actualSource.getId(), actualSink.getId()));
			}
		}
	}
	
	protected void comparePartitionInstances(IPartition actual) throws IOException {
		ISubgraph subgraph = actual.getSubgraph(34);

		// Retrieve all the instances
		{
			// Read the graph instances for vertex and edge properties
			Iterable<? extends ISubgraphInstance> subgraphInstances = subgraph.getInstances(Long.MIN_VALUE, Long.MAX_VALUE, subgraph.getVertexProperties(), subgraph.getEdgeProperties(), false);
			assertNotNull(subgraphInstances);

			// Assert the instances of subgraph id '34', returns the property
			// value for vertex 338
			for (ISubgraphInstance instance : subgraphInstances) {
				// Vertex Properties
				ISubgraphObjectProperties propertiesForVertex = instance.getPropertiesForVertex(338);
				// Edge Properties
				ISubgraphObjectProperties propertiesForEdge = instance.getPropertiesForEdge(3);

				if (instance.getId() == 0) {
					assertEquals("3390,7430", propertiesForVertex.getValue("license-list"));
					assertEquals(5.77, propertiesForVertex.getValue("latitude"));
					assertEquals(16.22, propertiesForVertex.getValue("longitude"));
					assertEquals(12, propertiesForEdge.getValue("weight"));
				} else if (instance.getId() == 1) {
					assertEquals("3380,7420", propertiesForVertex.getValue("license-list"));
					assertEquals(11.56, propertiesForVertex.getValue("latitude"));
					assertEquals(17.67, propertiesForVertex.getValue("longitude"));
					assertEquals(16, propertiesForEdge.getValue("weight"));
				}

			}
		}

		// Retrieve instance for the time range
		{
			// Start Time - 1367012492282
			// End Time - 1367012792282
			// Instance id - 0
			Iterable<? extends ISubgraphInstance> subgraphInstances = subgraph.getInstances(1367012492282L, 1367012792282L, subgraph.getVertexProperties(), subgraph.getEdgeProperties(), false);
			assertNotNull(subgraphInstances);
			// Assert the instances of subgraph id '34', returns the property
			// value for vertex 339
			for (ISubgraphInstance instance : subgraphInstances) {
				// Vertex Properties
				ISubgraphObjectProperties propertiesForVertex = instance.getPropertiesForVertex(339);
				// Edge Properties
				ISubgraphObjectProperties propertiesForEdge = instance.getPropertiesForEdge(3);
				assertEquals("3320,3380", propertiesForVertex.getValue("license-list"));
				assertEquals(5.77, propertiesForVertex.getValue("latitude"));
				assertEquals(16.22, propertiesForVertex.getValue("longitude"));
				assertEquals(12, propertiesForEdge.getValue("weight"));
			}
		}
	}
}
