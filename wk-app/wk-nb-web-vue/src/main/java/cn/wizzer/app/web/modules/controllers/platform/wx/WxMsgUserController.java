package cn.wizzer.app.web.modules.controllers.platform.wx;

import cn.wizzer.app.web.commons.ext.wx.WxService;
import cn.wizzer.app.web.commons.slog.annotation.SLog;
import cn.wizzer.app.web.commons.utils.PageUtil;
import cn.wizzer.app.wx.modules.models.Wx_config;
import cn.wizzer.app.wx.modules.models.Wx_msg_reply;
import cn.wizzer.app.wx.modules.services.WxConfigService;
import cn.wizzer.app.wx.modules.services.WxMsgReplyService;
import cn.wizzer.app.wx.modules.services.WxMsgService;
import cn.wizzer.framework.base.Result;
import com.alibaba.dubbo.config.annotation.Reference;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.nutz.dao.Cnd;
import org.nutz.ioc.loader.annotation.Inject;
import org.nutz.ioc.loader.annotation.IocBean;
import org.nutz.lang.Strings;
import org.nutz.log.Log;
import org.nutz.log.Logs;
import org.nutz.mvc.annotation.At;
import org.nutz.mvc.annotation.Ok;
import org.nutz.mvc.annotation.Param;
import org.nutz.weixin.bean.WxOutMsg;
import org.nutz.weixin.spi.WxApi2;
import org.nutz.weixin.spi.WxResp;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Created by Wizzer on 2016/7/8.
 */
@IocBean
@At("/platform/wx/msg/user")
public class WxMsgUserController {
    private static final Log log = Logs.get();
    @Inject
    @Reference
    private WxMsgService wxMsgService;
    @Inject
    @Reference
    private WxConfigService wxConfigService;
    @Inject
    @Reference
    private WxMsgReplyService wxMsgReplyService;
    @Inject
    private WxService wxService;

    @At({"/", "/?"})
    @Ok("beetl:/platform/wx/msg/user/index.html")
    @RequiresPermissions("wx.user.list")
    public void index(String wxid, HttpServletRequest req) {
        Wx_config wxConfig = null;
        List<Wx_config> list = wxConfigService.query(Cnd.NEW());
        if (list.size() > 0 && Strings.isBlank(wxid)) {
            wxConfig = list.get(0);
        }
        if (Strings.isNotBlank(wxid)) {
            wxConfig = wxConfigService.fetch(wxid);
        }
        req.setAttribute("wxConfig", wxConfig);
        req.setAttribute("wxList", list);
    }

    @At
    @Ok("json:full")
    @RequiresPermissions("wx.user.list")
    public Object data(@Param("wxid") String wxid, @Param("searchName") String searchName, @Param("searchKeyword") String searchKeyword, @Param("pageNumber") int pageNumber, @Param("pageSize") int pageSize, @Param("pageOrderName") String pageOrderName, @Param("pageOrderBy") String pageOrderBy) {
        Cnd cnd = Cnd.NEW();
        if (!Strings.isBlank(wxid)) {
            cnd.and("wxid", "=", wxid);
        }
        if (Strings.isNotBlank(searchName) && Strings.isNotBlank(searchKeyword)) {
            cnd.and(searchName, "like", "%" + searchKeyword + "%");
        }
        if (Strings.isNotBlank(pageOrderName) && Strings.isNotBlank(pageOrderBy)) {
            cnd.orderBy(pageOrderName, PageUtil.getOrder(pageOrderBy));
        }
        return Result.success().addData(wxMsgService.listPageLinks(pageNumber, pageSize, cnd, "reply"));
    }

    @At({"/replyDo/?"})
    @Ok("json")
    @RequiresPermissions("wx.user.list.sync")
    @SLog(tag = "回复微信", msg = "微信昵称:${args[2]}")
    public Object replyDo(String wxid, @Param("msgid") String msgid, @Param("nickname") String nickname, @Param("openid") String openid, @Param("replyContent") String content, HttpServletRequest req) {
        try {
            Wx_config config = wxConfigService.fetch(wxid);
            WxApi2 wxApi2 = wxService.getWxApi2(wxid);
            long now = System.currentTimeMillis() / 1000;
            WxOutMsg msg = new WxOutMsg();
            msg.setCreateTime(now);
            msg.setFromUserName(config.getAppid());
            msg.setMsgType("text");
            msg.setToUserName(openid);
            msg.setContent(content);
            WxResp wxResp = wxApi2.send(msg);
            if (wxResp.errcode() != 0) {
                return Result.error(wxResp.errmsg());
            }
            Wx_msg_reply reply = new Wx_msg_reply();
            reply.setContent(content);
            reply.setType("text");
            reply.setMsgid(msgid);
            reply.setOpenid(openid);
            reply.setWxid(wxid);
            Wx_msg_reply reply1 = wxMsgReplyService.insert(reply);
            if (reply1 != null) {
                wxMsgService.update(org.nutz.dao.Chain.make("replyId", reply1.getId()), Cnd.where("id", "=", msgid));
            }
            return Result.success();
        } catch (Exception e) {
            return Result.error();
        }
    }
}
