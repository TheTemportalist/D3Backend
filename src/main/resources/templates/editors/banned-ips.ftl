<div class="panel-body">
    <p>This file has no live updating.</p>

    <form class="form-inline" role="form" onsubmit="makeNew(); return false;">
        <div class="form-group">
            <label class="sr-only" for="newip">IP</label>
            <input type="text" class="form-control" id="newip" placeholder="IP">
        </div>
        <div class="form-group">
            <label class="sr-only" for="newSource">Source</label>
            <input type="text" class="form-control" id="newSource" placeholder="Source" value="Server">
        </div>
        <div class="form-group">
            <label class="sr-only" for="newReason">Reason</label>
            <input type="text" class="form-control" id="newReason" placeholder="Reason" value="Banned by an operator.">
        </div>
        <div class="form-group">
            <label class="sr-only" for="newCreated">Reason</label>
            <input type="text" class="form-control" id="newCreated" placeholder="Data Created" value="${Helper.getNowInBanFormat()?js_string}">
        </div>
        <div class="form-group">
            <label class="sr-only" for="newExpires">Reason</label>
            <input type="text" class="form-control" id="newExpires" placeholder="Expires" value="forever">
        </div>
        <button type="button" class="btn btn-info" id="addBtn" onclick="makeNew()">Add</button>
    </form>
    <table class="table table-hover">
        <thead>
        <tr>
            <th class="col-sm-1">IP</th>
            <th class="col-sm-1">Source</th>
            <th class="col-sm-2">Reason</th>
            <th class="col-sm-2">Created</th>
            <th class="col-sm-1">Expires</th>
            <th class="col-sm-1"></th>
        </tr>
        </thead>
        <tbody id="opList">
        </tbody>
    </table>
    <script type="text/javascript">
        var json = ${fm.getFileContents()};
        var opList = get("opList");
        json.forEach(function (object)
        {
            opList.innerHTML +=
                    "<tr id=\"" + object['ip'] + "\">" +
                    "<td>" + object['ip'] + "</td>" +
                    "<td>" + object['source'] + "</td>" +
                    "<td>" + object['reason'] + "</td>" +
                    "<td>" + object['created'] + "</td>" +
                    "<td>" + object['expires'] + "</td>" +
                    <#if !readonly>
                    "<td>" +
                    "<div class=\"btn-group\">" +
                    "<button type=\"button\" onclick=\"removeUser(\'" + object['ip'] + "\')\" class=\"btn btn-danger btn-xs\">Del</button>" +
                    "<button type=\"button\" onclick=\"makeEditable(\'" + object['ip'] + "\')\" class=\"btn btn-warning btn-xs\">Edit</button>" +
                    "</div>" +
                    "</td>"+
                    </#if>
                    "</tr>";
        });

        function removeUser(username)
        {
            var element = get(username);
            if (element != null) opList.removeChild(element);
            for (var i = json.length - 1; i >= 0; i--)
            {
                if (json[i]["ip"] === username)
                {
                    json.splice(i, 1);
                }
            }
        }

        function makeNew()
        {
            var xmlhttp = new XMLHttpRequest();
            var ip = get("newip").value;
            var source = get("newSource").value;
            var reason = get("newReason").value;
            var created = get("newCreated").value;
            var expires = get("newExpires").value;

            json.push({"ip": ip, "source": source, "reason": reason, "created": created, "expires": expires});
            opList.innerHTML +=
                    "<tr id=\"" + ip + "\">" +
                    "<td>" + ip + "</td>" +
                    "<td>" + source + "</td>" +
                    "<td>" + reason + "</td>" +
                    "<td>" + created + "</td>" +
                    "<td>" + expires + "</td>" +
                    <#if !readonly>
                    "<td>" +
                    "<div class=\"btn-group\">" +
                    "<button type=\"button\" onclick=\"removeUser(\'" + ip + "\')\" class=\"btn btn-danger btn-xs\">Del</button>" +
                    "<button type=\"button\" onclick=\"makeEditable(\'" + ip + "\')\" class=\"btn btn-warning btn-xs\">Edit</button>" +
                    "</div>" +
                    "</td>" +
                    </#if>
                    "</tr>";

            get("newip").value = "";
            get("newSource").value = "Server";
            get("newReason").value = "Banned by an operator.";
            get("newCreated").value = "${Helper.getNowInBanFormat()}";
            get("newExpires").value = "forever";

            get("addBtn").innerHTML = "Add";
        }

        function makeEditable(username)
        {
            var element = get(username);
            if (element != null) opList.removeChild(element);
            for (var i = json.length - 1; i >= 0; i--)
            {
                if (json[i]["ip"] === username)
                {
                    get("newip").value = json[i]["ip"];
                    get("newSource").value = json[i]["source"];
                    get("newReason").value = json[i]["reason"];
                    get("newCreated").value = json[i]["created"];
                    get("newExpires").value = json[i]["expires"];

                    get("addBtn").innerHTML = "Re-add";

                    json.splice(i, 1);
                }
            }
        }

        var websocket = new WebSocket(wsurl("filemanager/${server.ID?js_string}/${fm.stripServer(fm.getFile())}"));
        websocket.onerror = function (evt)
        {
            addAlert("The websocket errored. Refresh the page!")
        };
        websocket.onclose = function (evt)
        {
            addAlert("The websocket closed. Refresh the page!")
        };
        websocket.onmessage = function (evt)
        {
            var temp = JSON.parse(evt.data);
            if (temp.status === "ok")
            {
                editor.setValue(temp.data);
                editor.clearSelection();
            }
            else
            {
                addAlert(temp.message);
            }
        };
        function send()
        {
            websocket.send(JSON.stringify({method: "set", args: [JSON.stringify(json)]}));
        }
    </script>
<#if !readonly>
    <button type="button" class="btn btn-primary btn-block" onclick="send()">Save</button>
<#else>
    <p>File is readonly.</p>
</#if>
</div>