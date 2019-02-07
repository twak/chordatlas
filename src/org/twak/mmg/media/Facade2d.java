package org.twak.mmg.media;
import org.twak.mmg.MOgram;
import org.twak.mmg.functions.AddLabel;
import org.twak.mmg.functions.BoundOBB;
import org.twak.mmg.functions.DivideOBB;
import org.twak.mmg.functions.DividePointOBB;
import org.twak.mmg.functions.EdgeRandomPoint;
import org.twak.mmg.functions.EdgesToFace;
import org.twak.mmg.functions.Erase;
import org.twak.mmg.functions.FixedEdge;
import org.twak.mmg.functions.FixedLinearForm;
import org.twak.mmg.functions.FixedNum;
import org.twak.mmg.functions.FixedOBB;
import org.twak.mmg.functions.FixedPoint;
import org.twak.mmg.functions.FixedSegmentPath;
import org.twak.mmg.functions.FourLinearOBB;
import org.twak.mmg.functions.LabelToFace;
import org.twak.mmg.functions.LiftTwoPt1Path;
import org.twak.mmg.functions.LiftTwoPt1TwoPoint;
import org.twak.mmg.functions.OBBExportEdge;
import org.twak.mmg.functions.OBBExportLinearForm;
import org.twak.mmg.functions.OBBExportSegments;
import org.twak.mmg.functions.OffsetFace;
import org.twak.mmg.functions.OffsetLinear;
import org.twak.mmg.functions.OffsetOBB;
import org.twak.mmg.functions.OffsetPointPath;
import org.twak.mmg.functions.PathExportPoints;
import org.twak.mmg.functions.PathSection;
import org.twak.mmg.functions.PathTranslate;
import org.twak.mmg.functions.PointAbsOffset;
import org.twak.mmg.functions.PointCircle;
import org.twak.mmg.functions.PointLinear;
import org.twak.mmg.functions.PointLinearLinear;
import org.twak.mmg.functions.PointOffset;
import org.twak.mmg.functions.PointOpposite;
import org.twak.mmg.functions.PointRandomLinear;
import org.twak.mmg.functions.ProjectPointEdge;
import org.twak.mmg.functions.ProjectPointLinear;
import org.twak.mmg.functions.ProjectPointLinearOffset;
import org.twak.mmg.functions.ProjectPointPath;
import org.twak.mmg.functions.RepeatOBB;
import org.twak.mmg.functions.RepeatPointOBB;
import org.twak.mmg.functions.SegmentOBBDistance;
import org.twak.mmg.functions.SegmentRandomPoint;
import org.twak.mmg.functions.SplitLinearAbs;
import org.twak.mmg.functions.SplitLinearRel;
import org.twak.mmg.functions.SplitPathAbs;
import org.twak.mmg.functions.ThreeLinearOffsetLinear;
import org.twak.mmg.functions.ThreePointOBB;
import org.twak.mmg.functions.TweenLinear;
import org.twak.mmg.functions.TwoEdgePoint;
import org.twak.mmg.functions.TwoLinearPoint;
import org.twak.mmg.functions.TwoPointCirclePath;
import org.twak.mmg.functions.TwoPointEdge;
import org.twak.mmg.functions.TwoPointLinear;
import org.twak.mmg.functions.TwoPointSegment;

/**
 *
 * @author twak
 */
public class Facade2d extends Medium {
	
    public Facade2d()
    {
        super( "façade 2d", new Class[]
                {
                    FixedPoint.class, TwoPointLinear.class, PointLinear.class, PointLinearLinear.class,
                    ProjectPointLinearOffset.class, PointOpposite.class, PointOffset.class, 
                    PointAbsOffset.class, PointRandomLinear.class,
                    FixedLinearForm.class, SplitLinearAbs.class, SplitLinearRel.class, OffsetLinear.class, 
                    TwoLinearPoint.class, ProjectPointLinear.class,
                    ThreeLinearOffsetLinear.class, TweenLinear.class,
                    
                    FixedOBB.class, ThreePointOBB.class, FourLinearOBB.class, OBBExportLinearForm.class, OBBExportEdge.class, 
                    DivideOBB.class, RepeatOBB.class, FixedNum.class,
                    OffsetOBB.class, DividePointOBB.class, RepeatPointOBB.class, BoundOBB.class,
                    
                    TwoPointEdge.class, EdgeRandomPoint.class, FixedEdge.class,
                    ProjectPointEdge.class, TwoEdgePoint.class, EdgesToFace.class,
                    
                    LiftTwoPt1TwoPoint.class, LiftTwoPt1Path.class,
                    
                    OffsetFace.class,
                    
                    Erase.class,
                    AddLabel.class,
                    LabelToFace.class,
                });
        
    }

    @Override
    public RenderData getDefaultRenderData( ) {
    	return new DepthColor();
    }
    
    public void doRender( MOgram mogram ) {
    	if (renderListener != null)
    		renderListener.doRender( mogram );
    }
    
    public interface RenderListener {
		public void doRender( MOgram mogram );
    }

    RenderListener renderListener;
    
	public void setRenderListener( RenderListener renderListener ) {
		this.renderListener = renderListener;
	}
}
