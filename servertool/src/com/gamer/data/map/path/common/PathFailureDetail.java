package com.gamer.data.map.path.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 无路径时的最终卡点及其直接阻挡。
 */
public final class PathFailureDetail {

    private static final PathFailureDetail NONE = new PathFailureDetail(null, null, Collections.emptyList());

    private final PathGridPoint stuckGrid;//卡点网格

    private final PathWorldPoint stuckWorld;//卡点大陆

    private final List<PathBlockReason> blockReasons;//阻挡原因

    public PathFailureDetail(PathGridPoint stuckGrid, PathWorldPoint stuckWorld, List<PathBlockReason> blockReasons) {
        this.stuckGrid = stuckGrid;
        this.stuckWorld = stuckWorld;
        this.blockReasons = blockReasons == null ? Collections.emptyList()
            : Collections.unmodifiableList(new ArrayList<>(blockReasons));
    }

    public static PathFailureDetail none() {
        return NONE;
    }

    public static void addUniqueReason(List<PathBlockReason> reasons, PathBlockReason reason) {
        if (reasons == null || reason == null || !reason.isPresent()) {
            return;
        }
        for (PathBlockReason existing : reasons) {
            if (existing.getType() == reason.getType() && existing.getWorldX() == reason.getWorldX()
                && existing.getWorldZ() == reason.getWorldZ() && existing.getCol() == reason.getCol()
                && existing.getRow() == reason.getRow()) {
                return;
            }
        }
        reasons.add(reason);
    }

    public boolean isPresent() {
        return stuckGrid != null || stuckWorld != null || !blockReasons.isEmpty();
    }

    public PathGridPoint getStuckGrid() {
        return stuckGrid;
    }

    public PathWorldPoint getStuckWorld() {
        return stuckWorld;
    }

    public List<PathBlockReason> getBlockReasons() {
        return blockReasons;
    }

    public PathBlockReason getPrimaryBlockReason() {
        return blockReasons.isEmpty() ? PathBlockReason.none() : blockReasons.get(0);
    }
}
