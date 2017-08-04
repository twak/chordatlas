package org.twak.tweed;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import com.jme3.math.Quaternion;
import com.jme3.math.Vector3f;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.converters.reflection.PureJavaReflectionProvider;

public class TweedSettings {

	public static TweedSettings settings = new TweedSettings();
	static File folder;
	
	public Vector3f cameraLocation = new Vector3f(575.0763f, 159.23715f, -580.0377f);
	public Quaternion cameraOrientation = new Quaternion(0.029748844f, 0.9702514f, -0.16988836f, 0.16989778f);
	public int cameraSpeed = 0;

//	public double trans[] = new double[] { 982744.4803613932, 201433.17395506793 };  // ny
//	public String gmlCoordSystem = "EPSG:2263";
//	public double trans[] = new double[] { 440435.9,4473718.3 }; // madrid
//	public String gmlCoordSystem = "EPSG:3042";
//	public double trans[] = new double[] { 529665.78, 181912.16 }; // regent
//	public String gmlCoordSystem = "EPSG:27700"; 
//	public double trans[] = new double[] { 261826.04,665079.33 }; // glasgow
//	public String gmlCoordSystem = "EPSG:27700"; 

	
	public double trans[] = new double[] { 121659.721974586034776,486774.576303347887006 }; // amsterdam
	public String gmlCoordSystem = "EPSG:28992";
	
//	public double trans[] = new double[] { 426138.059429821,975725.769060029 }; // oviedo
//	public String gmlCoordSystem = "EPSG:2062"; 

//	public double trans[] = new double[] { 13506158.343432899564505,326522.905302504834253 }; // detroit
//	public String gmlCoordSystem = "EPSG:2253"; 
	
	public boolean flipFootprints = true;
	public double ambient = 0.5;
	public boolean ortho = false;
	public int fov = 0;

	public boolean calculateFootprintNormals = true;
	public double snapFootprintVert = 0;
	public boolean SSAO = true;
	
	public double megaFacadeAreaThreshold = 30; // 23 for regent
	public double profileHSampleDist = 0.2;
	public double profileVSampleDist = 0.5;
	public double profilePrune = 0.3;
	public double meshHoleJumpSize = 3;
	public double badGeomDist = 1.5;
	public double badGeomAngle = 0.5; // radians
	public double miniSoftTol = 2.5;
	
	public boolean useGis = true;
	public double miniWidthThreshold = 2;
	public int profileCount = 30;
	public double exposedFaceThreshold = 0.4;
	public double heightThreshold = 4;
	public double gisThreshold = 0.8;
	public double megafacacadeClusterGradient = 3;
	
	public TweedSettings() {
	}

	public static void load( File folder ) {
		TweedSettings.folder = folder;
		try {
			settings = (TweedSettings) new XStream(new PureJavaReflectionProvider()).fromXML( new File( folder, "tweed.xml" ) );
		} catch ( Throwable th ) {
			settings = new TweedSettings();
			save();
			th.printStackTrace();
		}
	}

	public static void save() {
		if (folder != null) {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream( new File( folder, "tweed.xml" ) );
				TweedSettings.settings.badGeomAngle = -0.1;
				new XStream(new PureJavaReflectionProvider()).toXML( TweedSettings.settings, fos );
			} catch ( Throwable e ) {
				e.printStackTrace();
			}
			finally {
				if (fos != null)
					try {
						fos.close();
					} catch ( IOException e ) {
						e.printStackTrace();
					}	
			}
		}
	}

}
