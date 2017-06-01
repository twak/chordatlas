package org.twak.viewTrace;

public class SliceParameters {

	public int FL_BINS = 80;
	public double FL_NEAR_LINE = 0.3;
	public double FL_GAP_TOL   = 0.2;
	public double FL_NEAR_LINE_2 = 0.15;
	public boolean FL_REGRESS = true;
	
	double CON_TOL = 0.2;
	
	public double CL_HIGH = 0.01;
	public double CL_LOW = 0.08;
	
	double scale = 1;
	public double MIN_LINES = 1;
	
	public SliceParameters(){}
	
	public SliceParameters(double distScale) {
		FL_NEAR_LINE *= distScale;
		FL_NEAR_LINE_2 *= distScale;
		FL_GAP_TOL *= distScale;
		
		CON_TOL *= distScale;
		
		CL_HIGH *= distScale;
		CL_LOW *= distScale;
	}

	public void setScale( double distScale ) {
		
		System.out.println("setting parameter scales to " + distScale) ;
		
		SliceParameters def = new SliceParameters();
		
		this.scale = distScale;
		
		FL_NEAR_LINE   = def.FL_NEAR_LINE   * scale;
		FL_NEAR_LINE_2 = def.FL_NEAR_LINE_2 * scale;
		FL_GAP_TOL     = def.FL_GAP_TOL     * scale;
		
		CON_TOL        = def.CON_TOL        * scale;
		
		CL_HIGH        = def.CL_HIGH        * scale;
		CL_LOW         = def.CL_LOW         * scale;
	}

	public double getScale() {
		return scale;
	}
	
}
