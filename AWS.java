// Modelo de optimización para minimizar makespan de cualquer tipo de aplicação
//Resolvendo a relaxação da variavel inteira
// So tem 1 aplicacao sendo executada na rede

// v.0.20


package samplesCPLEX;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import ilog.concert.IloConversion;
import ilog.concert.IloException;
import ilog.concert.IloIntVar;
import ilog.concert.IloLinearIntExpr;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloNumVarType;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplexModeler;

public class AWS
{
	public static void main(String[] args) throws IOException {

		int numberOfTasks = 3; 																										 					 // Number of task that make up the application
		int numberOfHosts = 13; 																										 				 // Processing host of the network. Fog 1, fog 2, Isolated node, and Cloud		
		int CoS = 7; 																															 				 // Class of Service of the Fog Application
		
	// double[] timePerInstructionHosts = {20, 20, 20, 20, 40, 40, 40, 20, 20, 20, 10, 10, 10}; // Expressed as (instructions/unit_time)^-1
		double[] timePerInstructionHosts = {2, 2, 2, 2, 4, 4, 4, 2, 2, 2, 1, 1, 1}; // Expressed as (instructions/unit_time)^-1
		double[] ramHosts = {8000, 8000, 8000, 8000, 4000, 4000, 4000, 8000, 8000, 8000, 16000, 16000, 16000}; 				                 	 						// RAM in MB
		double[] diskHosts = {80000, 80000, 80000, 80000, 40000, 40000, 40000, 80000, 80000, 80000, 160000, 160000, 160000}; 		 								 						// Disk space in MB
		double[] instructionsTasks = {2, 2, 2}; 					        							 						// Instructions to be process in each task I{j} in MIPS
		double[] ramTasks = {512, 512, 512}; 																		 					// Amount of RAM required by each task M{j} in MB
		double[] diskTasks = {2, 2, 2}; 									                 						      // Disk space required by each task D{j} in MB
			
                                     // j0 j1 j2
		double[][] bytesTransferredTasks = {{0, 5, 0},  // j0
                                        {0, 0, 5},  // j1
                                        {0, 0, 0}}; // j2 		

// Transmission time between two nodes			
	                                 // h0  h1  h2  h3  h4  h5  h6  h7  h8  h9 h10 h11 h12          // Links bw in (bits/units of time)^-1
double[][] timeToTransmitBitHosts = {{0,  2,  5,  5,  2,  5,  5,  7,  4,  7,  9,  6,  9},// h0 
       															 {2,  0,  3,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0},// h1 
                                     {5,  3,  0,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0},// h2
                                     {5,  3,  3,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0},// h3
                                     {2,  0,  0,  0,  0,  3,  3,  5,  8,  8, 10, 13, 13},// h4
                                     {5,  0,  0,  0,  3,  0,  3,  5,  8,  8, 10, 13, 13},// h5
                                     {5,  0,  0,  0,  3,  3,  0,  2,  5,  5,  7, 10, 10},// h6
                                     {7,  0,  0,  0,  5,  5,  2,  0,  3,  3,  5,  8,  8},// h7
                                     {4,  0,  0,  0,  8,  8,  5,  3,  0,  3,  5,  8,  8},// h8
                                     {7,  0,  0,  0,  8,  8,  5,  3,  3,  0,  2,  5,  5},// h9
                                     {9,  0,  0,  0, 10, 10,  7,  5,  5,  2,  0,  3,  3},// h10
		                                 {6,  0,  0,  0, 13, 13, 10,  8,  8,  5,  3,  0,  3},// h11	
		                                 {9,  0,  0,  0, 13, 13, 10,  8,  8,  5,  3,  3,  0}};// h12	

// Propagation time from h0 to k			
                                 // h0  h1  h2  h3  h4  h5  h6  h7  h8  h9 h10 h11 h12           // Propagation time in units of time
double[] timeToPropagateFrom0ToK = {0,  2,  5,  8,  2,  5,  8,  10,  4,  7,  9,  6,  12}; // h0 

			
// ========================================================================================================================================

		// Select the set of nodes that can process the workload according to the CoS of the application			
		int lowerBoundK = 0;
		int upperBoundK = 0; 
		
		System.out.println("CoS = " + CoS);
		switch (CoS) {
		case 1:
			lowerBoundK = 1;			
			upperBoundK = 4;	
			break;
		case 2: case 3:
			lowerBoundK = 4;			
			upperBoundK = 7;
			break;
		case 4: case 5:
			lowerBoundK = 4;
			upperBoundK = 10;
			break;
		case 6: 
			lowerBoundK = 4;
			upperBoundK = 13;
			break;
		case 7:
			lowerBoundK = 10;			
			upperBoundK = 13;	
			break;  
		default:
			System.out.println("Invalid CoS.");
			break;
		}
		
		System.out.println("Vh={" + lowerBoundK + "<=k<"+ upperBoundK + "}");
		
// =======================================================================================================================================

	// Calculate Tmax	
		
		
		// Find the fastest host of the network and the minimal processing time per instruction among hosts
		double minTimePerInstructionHosts; 
		int fastestHost;		
		minTimePerInstructionHosts = timePerInstructionHosts[lowerBoundK];
		fastestHost = 0;
		for(int i = lowerBoundK + 1; i < upperBoundK; i++){	
			if(timePerInstructionHosts[i] < minTimePerInstructionHosts) {
				minTimePerInstructionHosts = timePerInstructionHosts[i];
				fastestHost = i;					
			}
			minTimePerInstructionHosts = timePerInstructionHosts[i];
			fastestHost = i;	
		}		
		System.out.println("El nodo que procesa más rápido dentro de Vh es h" + fastestHost);

		
		// Finding the farest host of the set Vh' from the host h0
		int farestHostVh = 0; 
		double maxPropagationTimeInVh = Double.MIN_VALUE;
		int row0 = 0;
		for (int col = 0; col < timeToPropagateFrom0ToK.length; col++) {
			if((col>=lowerBoundK) && (col<upperBoundK)) {
				double value = timeToPropagateFrom0ToK[col];
				if (value >= maxPropagationTimeInVh) {
					maxPropagationTimeInVh = value;
					farestHostVh = col;		
				}			
			}
		}
		System.out.println("El nodo más alejado de h0 dentro del conjunto Vh es h" + farestHostVh);			

		
		// Computing the total amount of instructions and the total amount of disk space of the tasks
		double sumInstructions = 0;
		double sumDisk = 0;
		for (int j = 0; j < numberOfTasks; j++) {
			sumInstructions += instructionsTasks[j];     				// a=a+b => a+=b
			sumDisk += diskTasks[j];
		}			

		// Computing the maximum number of bits to be transferred between two subsequent tasks i and j:
		double maxBytesTransferredTasks = Double.MIN_VALUE;
		for (int row = 0; row < bytesTransferredTasks.length; row++) {
			for (int col = 0; col < bytesTransferredTasks[row].length; col++) {
				double value = bytesTransferredTasks[row][col];
				if (value > maxBytesTransferredTasks) {
					maxBytesTransferredTasks = value;
					System.out.println("Máximo número de bits a ser transferido entre las tareas i y j es = " + (int)maxBytesTransferredTasks + "\n");		            
					System.out.println("***********************************************************************\n");            
				}
			}
		}		
		
      int Tmax = (int)((sumDisk*timeToTransmitBitHosts[0][farestHostVh])+((timeToPropagateFrom0ToK[farestHostVh])*(numberOfTasks-1))+(minTimePerInstructionHosts * sumInstructions)+((numberOfTasks-1)* maxBytesTransferredTasks*timeToTransmitBitHosts[lowerBoundK][upperBoundK-1]));     
     
      System.out.println("sumDisk = "  + sumDisk);
      System.out.println("timeToTransmitBitHosts[0][farestHostVh] = " + timeToTransmitBitHosts[0][farestHostVh]); 
      System.out.println("farestHostVh = " + farestHostVh);
      System.out.println("minTimePerInstructionHosts = "  + minTimePerInstructionHosts);
      System.out.println("sumInstructions = "  + sumInstructions);
      System.out.println("maxBytesTransferredTasks = "  + maxBytesTransferredTasks);
      System.out.println("timeToTransmitBitHosts[lowerBoundK][upperBoundK-1] = " + timeToTransmitBitHosts[lowerBoundK][upperBoundK-1]);
      System.out.println("Tmax= " + Tmax + "\n");		      
			System.out.println("***********************************************************************\n"); 	

// =======================================================================================================================================

		try {
			// define new model
			IloCplex cplex = new IloCplex();
				
			// Defining variable X
			
			IloNumVar[][][] x = new IloNumVar[numberOfTasks][][];
			// loop for each task
			for (int j = 0; j < numberOfTasks; j++) {
				x[j] = new IloIntVar[(int) (Tmax + 1)][];
				// loop for each time slot					
				for (int t = 0; t <= Tmax; t++) {
					x[j][t] = new IloIntVar[upperBoundK];
					// loop for each host
					for (int k = lowerBoundK; k < upperBoundK; k++) {
						String label = "x[" + j + "," + t + "," + k + "]";
						// System.out.println(label);	
						x[j][t][k] = cplex.numVar(0, 1, label);
					}
				}
			}	
			
// ========================================================================================================================================

		// Creating the objective function
			IloLinearNumExpr Cmax = cplex.linearNumExpr();	
				for (int t = 0; t <= Tmax; t++) {
					for (int k = lowerBoundK; k < upperBoundK; k++) {	
						double transmissionTimejk = diskTasks[numberOfTasks-1]*timeToTransmitBitHosts[0][k];
						double propagationTimejk = timeToPropagateFrom0ToK[k];
						double processingTimejk = instructionsTasks[numberOfTasks-1] * timePerInstructionHosts[k];							
						double sumTTransmissionPropagationProcessingjk = (t+transmissionTimejk+propagationTimejk+processingTimejk);			
				
						Cmax.addTerm(x[numberOfTasks-1][t][k], (int) sumTTransmissionPropagationProcessingjk);
					}
				}
				
				// System.out.println("Cmax = " + Cmax + "\n");
				// set the CPLEX to minimize the objective
				cplex.addMinimize(Cmax);		

// ================================== Defining constraints D1-D6 ======================================================================

			// Defining expression D1
			// For all...
			for (int j = 0; j < numberOfTasks; j++) {			 

				// Create expression D1				
				IloLinearNumExpr expressionD1 = cplex.linearNumExpr();		
				for (int t = 0; t <= Tmax; t++) {
					for (int k = lowerBoundK; k < upperBoundK; k++) {											 
						expressionD1.addTerm(x[j][t][k], 1);
					}	
				}
				// Add the expression D1 to model
				cplex.addEq(expressionD1, 1);
				//  System.out.println("expressionD1= " + expressionD1);
			}		

			// cplex.setParam(IloCplex.Param.Simplex.Display, 1);			

// ---------------------------------------------------------------------------------------------------------------------------------------
		
			// Defining expression D2
			// For all...							 
			// Create the expression for minimize the time to send and process the application	
			for (int j = 0; j < numberOfTasks; j++) {	
				for (int k = lowerBoundK; k < upperBoundK; k++) {		
					
				  // Create expressionD2
					double processingTimejk = instructionsTasks[j] * timePerInstructionHosts[k];
					IloLinearNumExpr expressionD2 = cplex.linearNumExpr();
					for (int t = 0; t <= Tmax; t++) {							
						double transmissionTimejk = diskTasks[j]*timeToTransmitBitHosts[0][k];	
						double propagationTimejk = timeToPropagateFrom0ToK[k];
						double sumTTransmissionPropagationProcessingjk = (t+transmissionTimejk+propagationTimejk+processingTimejk);			
						expressionD2.addTerm((int) sumTTransmissionPropagationProcessingjk, x[j][t][k]);					 
					}
					// System.out.println("expressionD2= " + expressionD2);
						// Add the expression D2 to model
						cplex.addLe(expressionD2, Cmax);
				}					
			}	

// ---------------------------------------------------------------------------------------------------------------------------------------

		// Defining expression D3
		// For all...
		for (int k = lowerBoundK; k < upperBoundK; k++) {				
			for (int t = 0; t <= Tmax; t++) {	

				// Create expression D3
				IloLinearNumExpr expressionD3 = cplex.linearNumExpr();	
				for (int j = 0; j < numberOfTasks; j++) {
					double processingTimejk = instructionsTasks[j] * timePerInstructionHosts[k];	
					for (int s = t; s <= t+processingTimejk-1; s++) {
						if(t<=Tmax-processingTimejk) {
						expressionD3.addTerm(x[j][s][k], 1); 							
						}	
					}	
				}
				
				// Add the expression D3 to model
				cplex.addLe(expressionD3, 1);
				//System.out.println("expression= " + uexpression);	
			}
		}
		
   // cplex.setParam(IloCplex.Param.Simplex.Display, 1);	

// ---------------------------------------------------------------------------------------------------------------------------------------
		
		// Defining expression D4
		// For all...	
		for (int i = 0; i < numberOfTasks; i++) {
			for (int j = 0; j < numberOfTasks; j++) {	
				if (bytesTransferredTasks[i][j] !=0){
					for (int k = lowerBoundK; k < upperBoundK; k++) {				
						for (int t = 0; t <= Tmax; t++) {	

							//left			
							IloLinearNumExpr expressionRight = cplex.linearNumExpr();
							for (int s = 0; s <= t; s++) {	
								expressionRight.addTerm(x[j][s][k], 1);	
							}

							//right	
							IloLinearNumExpr expressionLeft = cplex.linearNumExpr();
							for (int h = lowerBoundK; h < upperBoundK; h++) {
								if ((timeToTransmitBitHosts[h][k]) != 0) {									
									double transmissionTimeih = diskTasks[i]*timeToTransmitBitHosts[0][h];
									double propagationTimeih = timeToPropagateFrom0ToK[h];
									double processingTimeih = instructionsTasks[i] * timePerInstructionHosts[h];	
									double datatransferTimeij = (bytesTransferredTasks[i][j]) * (timeToTransmitBitHosts[h][k]);			
									int limit = (int) Math.ceil(t-transmissionTimeih-propagationTimeih-processingTimeih-datatransferTimeij);
									for (int s = 0; s <= limit; s++) {
										expressionLeft.addTerm(x[i][s][h], 1);
									}
								}
							}
							//System.out.println("Left :" + expressionLeft);
							//	System.out.println("Right:" + expressionRight);
							cplex.addLe(expressionRight, expressionLeft);		
						}		
					}
				}	
			}
		}
		
// ---------------------------------------------------------------------------------------------------------------------------------------
		
			// Defining expression D5
			// For all...	
		  for (int k = lowerBoundK; k < upperBoundK; k++) {		
			
				// Create expression D5				
				IloLinearNumExpr expressionD5 = cplex.linearNumExpr();		
				for (int t = 0; t <= Tmax; t++) {
					for (int j = 0; j < numberOfTasks; j++) {			 										 
						expressionD5.addTerm(x[j][t][k], ramTasks[j]);
					}	
				}
				// Add the expression D5 to model
				cplex.addLe(expressionD5, ramHosts[k]);
				//  System.out.println("expressionD5= " + expressionD5);
			}	
		  
// ---------------------------------------------------------------------------------------------------------------------------------------
			
			// Defining expression D6
			// For all...	
		  for (int k = lowerBoundK; k < upperBoundK; k++) {		
			
				// Create expression D6				
				IloLinearNumExpr expressionD6 = cplex.linearNumExpr();		
				for (int t = 0; t <= Tmax; t++) {
					for (int j = 0; j < numberOfTasks; j++) {			 										 
						expressionD6.addTerm(x[j][t][k], diskTasks[j]);
					}	
				}
				// Add the expression D6 to model
				cplex.addLe(expressionD6, diskHosts[k]);
				//  System.out.println("expressionD6= " + expressionD6);
			}  
		  
// ========================================= Relaxation =======================================================================================
		
		// IloNumVar x = cplex.numVar(0.0, 1.0);
		// cplex.add(cplex.conversion(x, IloNumVarType.Int));
		
		// To modify the model, add the conversion object to the model
		//cplex.add(cplex.conversion(x, IloNumVarType.Float));		
		
//		// loop for each task
//		for (int j = 0; j < numberOfTasks; j++) {			
//			// loop for each time slot					
//			for (int t = 0; t <= Tmax; t++) {			
//				// loop for each host
//				for (int k = lowerBoundK; k < upperBoundK; k++) {
//					cplex.add(cplex.conversion(x[j][t][k], IloNumVarType.Float));
//				}
//			}
//		}			

// ========================================= Solve model =======================================================================================

		 cplex.exportModel("resultScheduling.lp"); // Imprime en un archivo todo el modelo de optimizacion columna a columna

			FileWriter salida = new FileWriter("resultScheduling.txt");
			PrintWriter escribirSalida = new PrintWriter(salida); // Inicializar
			
			if (cplex.solve()) {
				escribirSalida.println("\nSolution status = " + cplex.getStatus());
				System.out.println("\nSolution status = " + cplex.getStatus());
				escribirSalida.println("\nSolution objective = " + cplex.getObjValue() + "\n");
				System.out.println("\nSolution objective = " + cplex.getObjValue() + "\n");

				for (int j = 0; j < numberOfTasks; j++) {
					for (int t = 0; t <= Tmax; t++) {
						for (int k = lowerBoundK; k < upperBoundK; k++) {	
							
							if ((cplex.getValue(x[j][t][k])) == 1.0) {
							//if ((cplex.getValue(x[j][t][k])) >= 10e-6) {
								escribirSalida.println("Salida x[j][t][k] = " + x[j][t][k] + " = " + cplex.getValue(x[j][t][k]));										
								System.out.println("Salida x[j][t][k] = " + x[j][t][k] + " = " + cplex.getValue(x[j][t][k]));										
							}
						}
					}
				}
				
				System.out.println("Printout");				
			}
			else {
				escribirSalida.println("problem not solved");
				System.out.println("problem not solved");
			}
			salida.close();
			cplex.end();		
	}

// =============================== Concert exception ======================================================================================

		catch (IloException e) {
			System.err.println("Concert exception '" + e + "' caught");
		}
	}

// ========================================================================================================================================
}
