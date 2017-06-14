package org.twak.mmg.media;
import org.twak.mmg.MOgram;
import org.twak.mmg.functions.DivideOBB;
import org.twak.mmg.functions.DividePointOBB;
import org.twak.mmg.functions.Erase;
import org.twak.mmg.functions.FixedLinearForm;
import org.twak.mmg.functions.FixedOBB;
import org.twak.mmg.functions.FixedPoint;
import org.twak.mmg.functions.FixedSegmentPath;
import org.twak.mmg.functions.FourLinearOBB;
import org.twak.mmg.functions.LiftTwoPt1Path;
import org.twak.mmg.functions.LiftTwoPt1TwoPoint;
import org.twak.mmg.functions.OBBExportLinearForm;
import org.twak.mmg.functions.OBBExportSegments;
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
import org.twak.mmg.functions.TwoLinearPoint;
import org.twak.mmg.functions.TwoPointCirclePath;
import org.twak.mmg.functions.TwoPointLinear;
import org.twak.mmg.functions.TwoPointSegment;
import org.twak.mmg.functions.script.FixedNum;

/**
 *
 * @author twak
 */
public class Facade2d extends Medium {
	
    public Facade2d()
    {
        super( "sketch 2d", new Class[]
                {
                    FixedPoint.class, TwoPointLinear.class, PointLinear.class, PointLinearLinear.class, ProjectPointLinearOffset.class, PointOpposite.class, PointOffset.class, PointAbsOffset.class, PointRandomLinear.class,
                    FixedLinearForm.class, SplitLinearAbs.class, SplitLinearRel.class, OffsetLinear.class, TwoLinearPoint.class, ProjectPointLinear.class,
                    ThreeLinearOffsetLinear.class, TweenLinear.class,
                    FixedOBB.class, ThreePointOBB.class, FourLinearOBB.class, OBBExportLinearForm.class, OBBExportSegments.class, DivideOBB.class, RepeatOBB.class, FixedNum.class,
                    OffsetOBB.class, DividePointOBB.class, RepeatPointOBB.class,
                    TwoPointSegment.class, SegmentRandomPoint.class, FixedSegmentPath.class,
                    ProjectPointPath.class, OffsetPointPath.class, SplitPathAbs.class, PathSection.class, PathTranslate.class,
                    SegmentOBBDistance.class,
                    PathExportPoints.class,
                    LiftTwoPt1TwoPoint.class, LiftTwoPt1Path.class,
                    TwoPointCirclePath.class, PointCircle.class,
                    Erase.class
                });
    }

    public void doRender( MOgram mogram ) {
    	
    }
    
//    public static class Extrude {
//    	Loop<Point2d> shape;
//    	double distance = 0.3;
//    	Color color;
//    }
//    
//    public abstract void render (List<Extrude> shapes);
}
