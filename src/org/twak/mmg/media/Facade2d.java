package org.twak.mmg.media;
import org.twak.mmg.MOgram;
import org.twak.mmg.functions.*;
import org.twak.mmg.functions.old.*;
import org.twak.mmg.functions.ui.*;

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

                    DrawEdge.class, TwoPointEdge.class, EdgeRandomPoint.class,

                    ProjectPointEdge.class, TwoEdgePoint.class, EdgesToFace.class,

                    FixedLabel.class,
                    
                    LiftTwoPt1TwoPoint.class, LiftTwoPt1Path.class, PtPtBox.class,

                    FaceSplit.class, FaceRelSplit.class, FaceBoolean.class, FaceExportEdges.class,
                    OffsetFace.class, CloneFace.class,

                    FourPointCurvePath.class,

                    Erase.class,
                    AddLabel.class,
                    LabelToFace.class,
                });

        scale= 10;
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
