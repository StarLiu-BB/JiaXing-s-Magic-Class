package com.zhixue.common.core.constant;

/**
 * 服务名称常量类
 * 作用：这个类专门用来存放各个服务的名字，方便大家统一使用。
 * 当一个服务需要调用另一个服务时，就用这里定义的名字来找到对方。
 * 这样就不用到处写死服务名字，修改起来也方便。
 */
public final class ServiceNameConstants {

    // 不让别人创建这个类的对象，因为只用来存常量
    private ServiceNameConstants() {
    }

    // 登录认证服务的名字
    public static final String AUTH_SERVICE = "zhixue-auth";
    // 网关服务的名字
    public static final String GATEWAY_SERVICE = "zhixue-gateway";
    // 系统管理服务的名字
    public static final String SYSTEM_SERVICE = "zhixue-system";
    // 课程管理服务的名字
    public static final String COURSE_SERVICE = "zhixue-course";
    // 媒体资源服务的名字
    public static final String MEDIA_SERVICE = "zhixue-media";
    // 互动交流服务的名字
    public static final String INTERACTION_SERVICE = "zhixue-interaction";
    // 订单服务的名字
    public static final String ORDER_SERVICE = "zhixue-order";
    // 营销推广服务的名字
    public static final String MARKETING_SERVICE = "zhixue-marketing";
    // 智能学习服务的名字
    public static final String AI_SERVICE = "zhixue-ai";
}

