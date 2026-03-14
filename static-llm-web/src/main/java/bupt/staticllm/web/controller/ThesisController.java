package bupt.staticllm.web.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/thesis")
@Tag(name = "Thesis", description = "毕业论文查看接口")
public class ThesisController {

    private static final String THESIS_PATH = "static/thesis.md";

    @Operation(summary = "获取论文原始 Markdown 内容")
    @GetMapping(value = "/raw", produces = MediaType.TEXT_PLAIN_VALUE + ";charset=UTF-8")
    public String getThesisRaw() {
        try {
            ClassPathResource resource = new ClassPathResource(THESIS_PATH);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception e) {
            log.error("读取论文文件失败", e);
            return "论文文件读取失败: " + e.getMessage();
        }
    }

    @Operation(summary = "获取论文渲染后的 HTML 页面（可直接在浏览器中阅读）")
    @GetMapping(value = "/html", produces = MediaType.TEXT_HTML_VALUE + ";charset=UTF-8")
    public String getThesisHtml() {
        try {
            // 1. 读取 Markdown
            ClassPathResource resource = new ClassPathResource(THESIS_PATH);
            String markdown;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                markdown = reader.lines().collect(Collectors.joining("\n"));
            }

            // 2. 使用 commonmark 渲染为 HTML（支持 GFM 表格）
            List<Extension> extensions = List.of(TablesExtension.create());
            Parser parser = Parser.builder().extensions(extensions).build();
            HtmlRenderer renderer = HtmlRenderer.builder().extensions(extensions).build();

            Node document = parser.parse(markdown);
            String htmlBody = renderer.render(document);

            // 3. 包装为完整的 HTML 页面
            return buildHtmlPage(htmlBody);

        } catch (Exception e) {
            log.error("渲染论文失败", e);
            return "<html><body><h1>论文渲染失败</h1><p>" + e.getMessage() + "</p></body></html>";
        }
    }

    /**
     * 构建美观的 HTML 页面，内嵌 CSS 样式
     */
    private static final String HTML_TEMPLATE = """
            <!DOCTYPE html>
            <html lang="zh-CN">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>毕业论文 - 基于静态分析与大语言模型协同的 Java 代码缺陷智能验证系统</title>
                <style>
                    * { margin: 0; padding: 0; box-sizing: border-box; }
                    body {
                        font-family: 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
                        line-height: 1.8; color: #333; background: #f5f5f5;
                    }
                    /* 左侧目录栏 */
                    #toc-sidebar {
                        position: fixed; top: 0; left: 0;
                        width: 240px; height: 100vh;
                        background: #fff; border-right: 1px solid #eee;
                        overflow-y: auto; overflow-x: hidden;
                        z-index: 100; transition: width 0.25s ease, opacity 0.25s ease;
                        padding: 16px 0;
                    }
                    #toc-sidebar.collapsed { width: 0; opacity: 0; pointer-events: none; }
                    #toc-sidebar .toc-title {
                        padding: 0 20px 12px; font-size: 13px; font-weight: 600;
                        color: #999; letter-spacing: 2px; text-transform: uppercase;
                        border-bottom: 1px solid #f0f0f0; margin-bottom: 8px;
                    }
                    #toc-sidebar a {
                        display: block; padding: 4px 20px; font-size: 13px;
                        color: #666; text-decoration: none;
                        border-left: 2px solid transparent;
                        transition: all 0.15s; line-height: 1.6;
                        white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
                    }
                    #toc-sidebar a:hover { color: #333; background: #fafafa; }
                    #toc-sidebar a.active {
                        color: #1890ff; border-left-color: #1890ff;
                        background: #f6f9ff; font-weight: 500;
                    }
                    #toc-sidebar a.toc-h3 { padding-left: 36px; font-size: 12px; color: #999; }
                    #toc-sidebar a.toc-h3.active { color: #1890ff; }
                    /* 收起/展开按钮 */
                    #toc-toggle {
                        position: fixed; top: 12px; left: 12px; z-index: 200;
                        width: 32px; height: 32px; border-radius: 6px;
                        border: 1px solid #ddd; background: #fff; color: #666;
                        cursor: pointer; font-size: 16px;
                        display: flex; align-items: center; justify-content: center;
                        transition: left 0.25s ease, background 0.15s;
                        box-shadow: 0 1px 4px rgba(0,0,0,0.06);
                    }
                    #toc-toggle:hover { background: #f5f5f5; color: #333; }
                    body:not(.toc-hidden) #toc-toggle { left: 248px; }
                    /* 正文区域 */
                    .container {
                        max-width: 860px; margin: 0 auto;
                        background: #fff; padding: 50px 70px;
                        box-shadow: 0 1px 8px rgba(0,0,0,0.06); border-radius: 4px;
                        margin-left: 280px; margin-right: 40px;
                        min-height: 100vh;
                        transition: margin-left 0.25s ease;
                    }
                    body.toc-hidden .container { margin-left: auto; margin-right: auto; }
                    /* 正文排版 */
                    h1 { font-size: 26px; text-align: center; margin-bottom: 8px; color: #1a1a1a; }
                    h2 { font-size: 21px; margin-top: 44px; margin-bottom: 16px; padding-bottom: 6px; border-bottom: 2px solid #1890ff; color: #1a1a1a; }
                    h3 { font-size: 17px; margin-top: 28px; margin-bottom: 10px; color: #333; }
                    h4 { font-size: 15px; margin-top: 18px; margin-bottom: 8px; color: #555; }
                    p { margin-bottom: 12px; text-align: justify; text-indent: 2em; }
                    blockquote { border-left: 3px solid #1890ff; padding: 8px 16px; margin: 14px 0; background: #f8fbff; color: #555; }
                    blockquote p { text-indent: 0; }
                    code { background: #f5f5f5; padding: 1px 5px; border-radius: 3px; font-family: 'JetBrains Mono','Fira Code',Consolas,monospace; font-size: 13px; color: #c7254e; }
                    pre { background: #282c34; color: #abb2bf; padding: 14px 18px; border-radius: 6px; overflow-x: auto; margin: 14px 0; line-height: 1.5; }
                    pre code { background: none; color: inherit; padding: 0; }
                    table { width: 100%%; border-collapse: collapse; margin: 14px 0; font-size: 13px; }
                    th, td { border: 1px solid #ddd; padding: 8px 10px; text-align: left; }
                    th { background: #fafafa; font-weight: 600; }
                    tr:nth-child(even) { background: #fafafa; }
                    ul, ol { margin: 10px 0 10px 2em; }
                    li { margin-bottom: 5px; }
                    strong { color: #1a1a1a; }
                    hr { border: none; border-top: 1px solid #e8e8e8; margin: 36px 0; }
                    /* 引用链接 */
                    a.cite-link { color: #1890ff; text-decoration: none; cursor: pointer; border-bottom: 1px dashed #1890ff; }
                    a.cite-link:hover { color: #096dd9; border-bottom-style: solid; }
                    .ref-highlight { background: #fffbe6 !important; transition: background 0.5s; }
                    a.ref-back-link { color: #1890ff; text-decoration: none; font-size: 12px; margin-left: 4px; opacity: 0.5; }
                    a.ref-back-link:hover { opacity: 1; }
                    /* 回到顶部 */
                    #back-top { position: fixed; bottom: 32px; right: 32px; width: 36px; height: 36px; border-radius: 8px; border: 1px solid #ddd; background: #fff; color: #666; cursor: pointer; font-size: 16px; display: none; align-items: center; justify-content: center; box-shadow: 0 1px 4px rgba(0,0,0,0.06); z-index: 99; }
                    #back-top:hover { background: #f5f5f5; color: #333; }
                    /* 滚动条美化 */
                    #toc-sidebar::-webkit-scrollbar { width: 3px; }
                    #toc-sidebar::-webkit-scrollbar-thumb { background: #ddd; border-radius: 3px; }
                    @media (max-width: 1100px) {
                        #toc-sidebar { display: none; }
                        #toc-toggle { display: none; }
                        .container { margin-left: auto; margin-right: auto; padding: 30px 24px; }
                    }
                    @media print {
                        #toc-sidebar, #toc-toggle, #back-top { display: none !important; }
                        .container { margin: 0; box-shadow: none; padding: 30px; }
                    }
                </style>
            </head>
            <body>
                <nav id="toc-sidebar"><div class="toc-title">目录</div><div id="toc-body"></div></nav>
                <button id="toc-toggle" title="收起/展开目录">☰</button>
                <button id="back-top" onclick="window.scrollTo({top:0,behavior:'smooth'})" title="回到顶部">↑</button>
                <div class="container">{{BODY_PLACEHOLDER}}</div>
                <script>
                // 生成目录
                (function(){
                    var box=document.querySelector('.container'),toc=document.getElementById('toc-body'),n=0;
                    box.querySelectorAll('h2,h3').forEach(function(h){
                        if(!h.id)h.id='s-'+(n++);
                        var a=document.createElement('a');
                        a.href='#'+h.id; a.textContent=h.textContent;
                        a.className='toc-'+h.tagName.toLowerCase();
                        a.setAttribute('data-t',h.id);
                        a.onclick=function(e){e.preventDefault();document.getElementById(h.id).scrollIntoView({behavior:'smooth'});};
                        toc.appendChild(a);
                    });
                })();
                // 滚动高亮
                (function(){
                    var links=document.querySelectorAll('#toc-body a'),items=[];
                    links.forEach(function(a){var e=document.getElementById(a.getAttribute('data-t'));if(e)items.push({el:e,link:a});});
                    window.addEventListener('scroll',function(){
                        var st=window.scrollY,cur=null;
                        for(var i=0;i<items.length;i++){if(items[i].el.offsetTop<=st+90)cur=items[i];}
                        links.forEach(function(a){a.classList.remove('active');});
                        if(cur){cur.link.classList.add('active');cur.link.scrollIntoView({block:'nearest'});}
                    });
                })();
                // 折叠目录
                document.getElementById('toc-toggle').onclick=function(){
                    document.body.classList.toggle('toc-hidden');
                    document.getElementById('toc-sidebar').classList.toggle('collapsed');
                };
                // 回到顶部
                window.addEventListener('scroll',function(){document.getElementById('back-top').style.display=window.scrollY>300?'flex':'none';});
                // 引用跳转
                (function(){
                    var box=document.querySelector('.container'),ps=box.querySelectorAll('p'),rm={};
                    ps.forEach(function(p){var m=p.textContent.match(/^\\[(\\d+)\\]/);if(m){p.id='ref-'+m[1];rm[m[1]]=p;}});
                    var w=document.createTreeWalker(box,NodeFilter.SHOW_TEXT,null,false),ns=[];
                    while(w.nextNode())ns.push(w.currentNode);
                    var re=/\\[(\\d+)\\]/g;
                    ns.forEach(function(nd){
                        var pa=nd.parentElement;if(!pa)return;
                        if(pa.closest('pre')||pa.closest('code'))return;
                        if(pa.id&&pa.id.startsWith('ref-'))return;
                        if(pa.closest('#toc-sidebar'))return;
                        var t=nd.textContent;if(!re.test(t))return;re.lastIndex=0;
                        var f=document.createDocumentFragment(),li=0,m;
                        while((m=re.exec(t))!==null){
                            var num=m[1];if(!rm[num])continue;
                            if(m.index>li)f.appendChild(document.createTextNode(t.substring(li,m.index)));
                            var a=document.createElement('a');a.className='cite-link';a.href='#ref-'+num;
                            a.textContent='['+num+']';a.title='参考文献 ['+num+']';
                            (function(n2){var sid='cs-'+n2+'-'+Math.random().toString(36).substr(2,5);a.id=sid;
                            a.onclick=function(e){e.preventDefault();var r=document.getElementById('ref-'+n2);
                            if(r){r.scrollIntoView({behavior:'smooth',block:'center'});r.classList.add('ref-highlight');
                            setTimeout(function(){r.classList.remove('ref-highlight');},2500);}}})(num);
                            f.appendChild(a);li=m.index+m[0].length;
                        }
                        if(li===0)return;if(li<t.length)f.appendChild(document.createTextNode(t.substring(li)));
                        pa.replaceChild(f,nd);
                    });
                    Object.keys(rm).forEach(function(num){
                        var b=document.createElement('a');b.className='ref-back-link';b.href='#';b.textContent=' ↩';b.title='返回正文';
                        b.onclick=function(e){e.preventDefault();var s=document.querySelector('[id^="cs-'+num+'-"]');
                        if(s){s.scrollIntoView({behavior:'smooth',block:'center'});s.style.background='#fffbe6';
                        setTimeout(function(){s.style.background='';},1500);}};rm[num].appendChild(b);
                    });
                })();
                </script>
            </body>
            </html>
            """;

    private String buildHtmlPage(String htmlBody) {
        return HTML_TEMPLATE.replace("{{BODY_PLACEHOLDER}}", htmlBody);
    }
}
