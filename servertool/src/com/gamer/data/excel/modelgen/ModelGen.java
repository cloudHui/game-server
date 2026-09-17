package com.gamer.data.excel.modelgen;

import java.util.List;

import com.gamer.data.excel.modelgen.generator.Generator;
import com.gamer.data.excel.shared.FileWithSheets;

/**
 * 模型代码生成门面，委托 {@link Generator} 执行。
 */
public final class ModelGen {

    private ModelGen() {}

    /**
     * 生成模型或枚举代码。
     *
     * @param allFiles
     *            待处理 Excel 列表
     * @param modelGenContext
     *            生成运行时上下文
     */
    public static void genCode(List<FileWithSheets> allFiles, ModelGenContext modelGenContext) {
        Generator.genCode(allFiles, modelGenContext, false);
    }

    /** 生成 Config + Manager/配置注册，弹框勾选。 */
    public static void genCodeAndConfig(List<FileWithSheets> allFiles, ModelGenContext modelGenContext) {
        Generator.genCode(allFiles, modelGenContext, true);
    }
}
