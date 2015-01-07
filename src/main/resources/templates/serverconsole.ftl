<!DOCTYPE html>
<!--
  ~ Unless otherwise specified through the '@author' tag or comments at
  ~ the top of the file or on a specific portion of the code the following license applies:
  ~
  ~ Copyright (c) 2014, DoubleDoorDevelopment
  ~ All rights reserved.
  ~
  ~ Redistribution and use in source and binary forms, with or without
  ~ modification, are permitted provided that the following conditions are met:
  ~
  ~ * Redistributions of source code must retain the above copyright notice, this
  ~   list of conditions and the following disclaimer.
  ~
  ~ * Redistributions in binary form must reproduce the above copyright notice,
  ~   this list of conditions and the following disclaimer in the documentation
  ~   and/or other materials provided with the distribution.
  ~
  ~ * The header specified or the above copyright notice, this list of conditions
  ~   and the following disclaimer below must be displayed at the top of the source code
  ~   of any web page received while using any part of the service this software provides.
  ~
  ~   The header to be displayed:
  ~       This page was generated by DoubleDoorDevelopment's D3Backend or a derivative thereof.
  ~
  ~ * Neither the name of the project nor the names of its
  ~   contributors may be used to endorse or promote products derived from
  ~   this software without specific prior written permission.
  ~
  ~ THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
  ~ AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
  ~ IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
  ~ DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
  ~ FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
  ~ DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
  ~ SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
  ~ CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  ~ OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
  ~ OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
  -->
<html>
<head lang="en">
    <meta charset="UTF-8">
    <title>Console ${server.ID}</title>
    <!-- Le meta -->
    <meta name="author" content="Dries007">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Le styles -->
    <link href="/static/css/bootstrap.min.css" rel="stylesheet">
    <link href="/static/css/font-awesome.min.css" rel="stylesheet">
    <link rel="shortcut icon" type="image/ico" href="/static/favicon.ico"/>
</head>
<body onresize="document.getElementById('text').style.height = (window.innerHeight - 35) + 'px';">
<textarea class="textarea form-control" id="text" style="height: 465px;"></textarea>
<input type="text" class="form-control" placeholder="Command..." onkeydown="if (event.keyCode == 13) {send(this.value); this.value = ''}">
<script>
    function wsurl(s)
    {
        var l = window.location;
        return (l.protocol === "https:" ? "wss://" : "ws://") + l.hostname + ":" + l.port + "/socket/" + s;
    }
    var textarea = document.getElementById("text");
    var autoScroll = true;
    var websocket = new WebSocket(wsurl("serverconsole/${server.ID}"));
    websocket.onerror = function (evt)
    {
        alert("The websocket errored. Refresh the page!")
    };
    websocket.onclose = function (evt)
    {
        alert("The websocket closed. Refresh the page!")
    };

    websocket.onmessage = function (evt)
    {
        var temp = JSON.parse(evt.data);
        if (temp.status !== "ok")
        {
            alert(temp.message);
        }
        else
        {
            autoScroll = textarea.scrollHeight <= textarea.scrollTop + textarea.clientHeight + 50;
            var total = ((textarea.value ? textarea.value + "\n" : "") + temp.data).split("\n");
            if (total.length > 1000) total = total.slice(total.length - 1000);
            textarea.value = total.join("\n");
            if (autoScroll) textarea.scrollTop = textarea.scrollHeight;
        }
    };

    function send(data)
    {
        websocket.send(data);
    }
</script>
</body>
</html>