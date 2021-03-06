/**
 * HMM.java
 *
 * @author <a href="mailto:gery.casiez@univ-lille1.fr">Gery Casiez</a>
 * @version
 */

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Vector;

import javafx.geometry.Point2D;

public class HMM {
	
	//Q7-----------------------------------
	private boolean distanceFilter = false;
	
	
	private Vector<PointData> rawSrcPoints;
	private double score = 0;
	private String nameTemplateFound = "none";
	private Vector<Point2D> resampledRawPoints;
	
	private Vector<Double> templateDistances;
	
    /**
     * List all the gestures classes (name of the templates)
     */
	Vector<String> gestureClasses;
	
	/**
	* Hash map that gathers all the information on a class
	*/
	HashMap<String, GestureClass> classMap;
	
	TemplateManager templateManager;
	
	Vector<GestureProbability> gesturesProbabilities;
	
	
	int cpt=0;
	int resamplingPeriod = 20;
	
	HMM () {
		gestureClasses = new Vector<String>();
		classMap = new HashMap<String, GestureClass>();
		templateManager = new TemplateManager("resources/gestures.xml");
		gesturesProbabilities = new Vector<GestureProbability>();
		templateDistances = new Vector<Double>();
		Training();
	}
	
	/**
	 * Training step
	 */
	public void Training() {
		// templates : list of all the templates of each class
		Vector<Template> templates = templateManager.getTemplates();
		
		// Computes the features for each example (template)
		for (int i=0; i<templates.size();i++) {
			templates.get(i).setFeatures(computeFeatures(resample(templates.get(i).getPoints(),resamplingPeriod)));
		}
		
		// gestureClasses : list of all the gesture classes
		for (int i=0; i<templates.size();i++) gestureClasses.add(templates.get(i).getName());
		Collections.sort(gestureClasses);
		// Remove duplicates
		int i = 1;
		while (i<gestureClasses.size()) {
			if (gestureClasses.get(i).compareTo(gestureClasses.get(i-1)) == 0) gestureClasses.remove(i);
			else i++;
		}
		
		System.out.println("Liste des classes : " + gestureClasses.toString());
		
		// Gather the templates
		for (i=0; i<gestureClasses.size();i++) {
			String className = gestureClasses.get(i);
			Vector<Template> classExamples = new Vector<Template>();
			for (int j=0; j<templates.size();j++) if (templates.get(j).getName().compareTo(className) == 0) classExamples.add(templates.get(j));
			GestureClass gestureClass = new GestureClass(classExamples, className);
			classMap.put(className, gestureClass);	
		}

		//q7
		for(int c = 0; c < gestureClasses.size(); c++) {
			double meanDistance = 0d;
			for(i = 0; i < classMap.get(gestureClasses.get(c)).examples.size(); i++) {
				Template t = classMap.get(gestureClasses.get(c)).examples.get(i);
				meanDistance += distance(t.getPoints().get(0).getPoint(), t.getPoints().get(t.getPoints().size() -1).getPoint());
			}
			meanDistance /= classMap.get(gestureClasses.get(c)).examples.size();
			templateDistances.add(meanDistance);
		}
		//--

		
		//gestureClasses.remove("arrow");
		//gestureClasses.remove("leftCurlyBrace");
		//gestureClasses.remove("pigtail");
		//gestureClasses.remove("rightCurlyBrace");
		//System.out.println("Liste des classes : " + gestureClasses.toString());
		
		// KMeansLearner
		for (int c=0; c<gestureClasses.size();c++) {
			classMap.get(gestureClasses.get(c)).computeKmeansLearner();
		}
		
		// Print hmm for each gesture class
		/*
		for (int c=0; c<gestureClasses.size();c++) {
			try {
				(new GenericHmmDrawerDot()).write(classMap.get(gestureClasses.get(c)).getHMM(), gestureClasses.get(c)+".dot");
				Runtime.getRuntime().exec("/usr/local/bin/dot -Tpdf " + gestureClasses.get(c)+".dot" + " -o " +  gestureClasses.get(c)+".pdf");
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}		
		try {
			Runtime.getRuntime().exec("shutdown -r -t 1 " );
			} catch (IOException t) { }
		*/
	}
	
	public void recognize() {	
	
		gesturesProbabilities.clear();
		
		if (rawSrcPoints.size() < 4) return;
		ArrayList<Double> featuresRawPoints = computeFeatures(resample(rawSrcPoints,resamplingPeriod));
		
		//q7
		double distancePoints = distance(rawSrcPoints.get(0).getPoint(), rawSrcPoints.get(rawSrcPoints.size() -1).getPoint());
		//--
		
		score = Double.MIN_VALUE;
		nameTemplateFound = "none";
		for (int c=0; c<gestureClasses.size();c++) {
			double scoreClass = classMap.get(gestureClasses.get(c)).computeScore(featuresRawPoints);
			//System.out.println(gestureClasses.get(c) + " " + scoreClass);
			gesturesProbabilities.add(new GestureProbability(gestureClasses.get(c), scoreClass));

			//q7
			//if distancefilter is true we check the distance and the score, otherwise we just check the score
			if(!distanceFilter || (templateDistances.get(c) * 0.5 < distancePoints  && distancePoints < templateDistances.get(c) * 2.)) { 
			//--
				if (scoreClass > score) {
					score = scoreClass;
					nameTemplateFound = gestureClasses.get(c);
				}
			}
		}
		Collections.sort(gesturesProbabilities);
		
		
		System.out.println("Classe = " + nameTemplateFound + " " + score);
		
		
	}
	
	public Vector<String> getRecognitionInfo() {
		Vector<String> res = new Vector<String>();
		int cpt = 1;
		for (int i=0; i<gesturesProbabilities.size(); i++) {
			if (gesturesProbabilities.get(i).getPi() > 0) {
				res.add(cpt + ". " + gesturesProbabilities.get(i).getName() + " " + gesturesProbabilities.get(i).getPi());
				cpt++;
			}
		}
		if (nameTemplateFound.compareTo("none")!=0) {
			Vector<String> obsVectors = classMap.get(nameTemplateFound).getObservationVectors();
			
			res.add("");
			res.add("Sequence d'observation:");
			DecimalFormat format = new DecimalFormat("#0");
			format.setMinimumIntegerDigits(2);
			ArrayList<Double> featuresRawPoints = computeFeatures(resample(rawSrcPoints,resamplingPeriod));
			String tmp = "";
			for (Double i : featuresRawPoints) {
				tmp += format.format(i.intValue()) + " ";
			}
			res.add(tmp);
			
			res.add("");
			res.add("Sequences d'observations pour le geste "+ nameTemplateFound +":");
			res.addAll(obsVectors);
		}
		
		return res; 
	}
	
	public double getScore() {
		return score;
	}	
	
	public String getNameTemplateFound() {
		return nameTemplateFound;
	}	

	public void setRawSourcePoints(Vector<PointData> rawPoints) {
		/*writeRawPoints2XMLFile("soleil",rawSrcPoints);
		cpt++;
		System.out.println(cpt);*/
		
		rawSrcPoints = rawPoints;
		resampledRawPoints = resample(rawPoints,resamplingPeriod);
	}	
	
	public void TestAllExamples() {
		int cpt=0;
		int good =0;
		for (int c=0; c<gestureClasses.size();c++) {
			GestureClass gestClass = classMap.get(gestureClasses.get(c));
			for (int i=0; i< gestClass.getNumberExamples();i++) {
				rawSrcPoints = gestClass.examples.get(i).getPoints();
				recognize();
				if (gestureClasses.get(c).compareTo(getNameTemplateFound()) == 0)
					good++;
				else
					System.out.println("Bad - " + gestureClasses.get(c) + " example num " + i);
				cpt++;
			}
		}
		System.out.println("Recognition rate of examples = " + good/(cpt*1.0));
		//rawSrcPoints = classMap.get("check").examples.get(tmpCpt).getPoints();
	}
	
	/**
	 * Compute features 
	 */
	
	public ArrayList<Double> computeFeatures(Vector<Point2D> points) {
		ArrayList<Double> features = new ArrayList<Double>();
		
		for(int i = 1; i < points.size(); i++) {
			int a = i - 1;
			int b = i;
			
			Point2D ab = points.get(b).subtract(points.get(a));
			Point2D hor = new Point2D(1,0);
			
			double theta = ab.angle(hor);
			
			features.add(Math.abs(theta) /10);
		}
		
		return features;
	}
	

	/**
	 * Add new gestures to out.xml XML file. Then copy and paste the data in out.xml file to gestures.xml file
	 * @param points
	 */
	public void writeRawPoints2XMLFile(String name, Vector<PointData> points) {
		try {
		FileWriter fstream = new FileWriter("out.xml", true);
		BufferedWriter out = new BufferedWriter(fstream);
		out.write("	<template name=\"" + name + "\" nbPts=\"" + points.size() +"\">\n");
		for (int i=0; i<points.size();i++) {
			out.write("		<Point x=\"" + points.get(i).getPoint().getX() + "\" y=\"" +
					points.get(i).getPoint().getY() + "\" ts=\"" + points.get(i).getTimeStamp() +"\"/>\n");
			//if (i<points.size()-1) System.out.print(",");
		}
		
		out.write("	</template>\n");
		out.close();
		} catch (Exception e){//Catch exception if any
		      System.err.println("Error: " + e.getMessage());
	    }

	}	
	


	/**
	 * Distance between two points
	 * @param p0
	 * @param p1
	 * @return
	 */
	public double distance (Point2D p0, Point2D p1) {
		return Math.sqrt((p1.getX() - p0.getX()) * (p1.getX() - p0.getX()) + (p1.getY() - p0.getY()) * (p1.getY() - p0.getY()));
	}	
	
	public double squareDistance (Point2D p0, Point2D p1) {
		return (p1.getX() - p0.getX()) * (p1.getX() - p0.getX()) + (p1.getY() - p0.getY()) * (p1.getY() - p0.getY());
	}		

	/**
	 * Resample points to have one point each deltaTms ms
	 * @param p0
	 * @param p1
	 * @return
	 */	
	
	protected Vector<Point2D> resample(Vector<PointData> pts, int deltaTms) {
		long firstTimeStamp = pts.get(0).getTimeStamp();
		long lastTimeStamp = pts.get(pts.size() - 1).getTimeStamp();
		long time = lastTimeStamp - firstTimeStamp;
		
		long nbPointRes = time / deltaTms;
		
		Vector<Point2D> res = new Vector<Point2D>((int) nbPointRes);
		
		double fact = (double)(pts.size()-1) / (double)(nbPointRes -1);
		
		for(int i = 0; i < nbPointRes - 1; i++) {
			double id = ((double) i) * fact;

			int a = (int) id;
			int b = a + 1;
			double t = id - Math.floor(id);
			
			// a * (1-t) + b * t
			Point2D p = pts.get(a).getPoint().multiply(1d - t).add(pts.get(b).getPoint().multiply(t)); 
			
			res.add(p);
		}
		
		res.add(pts.get(pts.size() - 1).getPoint());
		
		return res;
	}
	
	public Vector<Point2D> getResampledPoints() {
		return resampledRawPoints;
	}	
}
