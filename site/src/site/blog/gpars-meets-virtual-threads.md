---
layout: post
title: GPars meets Virtual Threads
date: '2022-06-15T11:28:56+00:00'
permalink: gpars-meets-virtual-threads
---
<p><img src="https://blogs.apache.org/groovy/mediaresource/52d821d8-f108-48e6-805d-f8fdd514f9ca" style="width: 20%;" align="right" alt="gpars-rgb.png">An exciting preview feature coming in JDK19 is Virtual Threads (<a href="https://openjdk.java.net/jeps/425" target="_blank">JEP 425</a>). In my experiments so far, virtual threads work well with my favourite Groovy parallel and concurrency library <a href="http://gpars.org/" target="_blank">GPars</a>. GPars has been around a while (since Java 5 and Groovy 1.8 days) but still has many useful features. Let's have a look at a few examples.</p><p>If you want to try these out, make sure you have a recent JDK19 (currently EA) and enable <i>preview </i>features with your Groovy tooling.</p>

<h3>Parallel Collections</h3>

<p>First a refresher, to use the GPars parallel collections feature with normal threads, use the
<span style="background-color: #FFFFFF; color: rgb(8, 8, 8); font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 14px;> GParsPool.<span style=" font-style="italic;&quot;">withPool</span> method as follows:
</p>

<pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="font-style:italic;">withPool </span><span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#0033b3;">assert </span>[<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">2</span>, <span style="color:#1750eb;">3</span>].collectParallel<span style="font-weight:bold;">{ </span>it ** <span style="color:#1750eb;">2 </span><span style="font-weight:bold;">} </span>== [<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">4</span>, <span style="color:#1750eb;">9</span>]
}</pre>

<p>For any Java readers, don't get confused with the <span style="font-family:'JetBrains Mono',monospace;font-size: 14px; color: rgb(8, 8, 8);">collectParallel</span> method name. Groovy's <span style="font-family:'JetBrains Mono',monospace;font-size: 14px; color: rgb(8, 8, 8);">collect</span> method (naming inspired by Smalltalk) is the equivalent of Java's <span style="font-family:'JetBrains Mono',monospace;font-size: 14px; color: rgb(8, 8, 8);">map</span> method. So, the equivalent Groovy code using the Java streams API would be something like:

</p><pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#0033b3;">assert </span>[<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">2</span>, <span style="color:#1750eb;">3</span>].parallelStream().map(n -&gt; n ** <span style="color:#1750eb;">2</span>).collect(<span style="color:#000000;">Collectors</span>.<span style="font-style:italic;">toList</span>()) == [<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">4</span>, <span style="color:#1750eb;">9</span>]<br></pre>

<p>Now, let's bring virtual threads into the picture. Luckily, GPars parallel collection facilities provide a hook for using an <i>existing </i>custom executor service. This makes using virtual threads for such code easy:</p><pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="font-style:italic;">withExistingPool</span>(<span style="color:#000000;">Executors</span>.newVirtualThreadPerTaskExecutor()) <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#0033b3;">assert </span>[<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">2</span>, <span style="color:#1750eb;">3</span>].collectParallel<span style="font-weight:bold;">{ </span>it ** <span style="color:#1750eb;">2 </span><span style="font-weight:bold;">} </span>== [<span style="color:#1750eb;">1</span>, <span style="color:#1750eb;">4</span>, <span style="color:#1750eb;">9</span>]<br><span style="font-weight:bold;">}<br></span></pre>
<p>Nice! But let's move onto some areas examples which might be less familiar to Java developers.</p><p>GPars has additional features for providing custom thread pools and the remaining examples rely on those features. The current version of GPars doesn't have a&nbsp;<span style="font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 9.6pt;">DefaultPool&nbsp;</span>constructor that takes a vanilla executor service, so, we'll write our own class:</p><pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#9e880d;">@AutoImplement<br></span><span style="color:#0033b3;">class </span><span style="color:#000000;">VirtualPool </span><span style="color:#0033b3;">implements </span><span style="color:#000000;">Pool </span>{<br>    <span style="color:#0033b3;">private final </span><span style="color:#000000;">ExecutorService </span><span style="color:#871094;">pool </span>= <span style="color:#000000;">Executors</span>.newVirtualThreadPerTaskExecutor()<br>    <span style="color:#0033b3;">int </span><span style="color:#00627a;">getPoolSize</span>() { <span style="color:#871094;">pool</span>.poolSize }<br>    <span style="color:#0033b3;">void </span><span style="color:#00627a;">execute</span>(<span style="color:#000000;">Runnable </span>task) { <span style="color:#871094;">pool</span>.execute(task) }<br>    <span style="color:#000000;">ExecutorService </span><span style="color:#00627a;">getExecutorService</span>() { <span style="color:#871094;">pool </span>}<br>}<br></pre><p>It is essentially a delegate from the GPars&nbsp;<span style="font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 9.6pt;">Pool</span>&nbsp;interface to the virtual threads executor service.</p><p>We'll use this in the remaining examples.</p>

<h3>Agents</h3>
<p>Agents provide a thread-safe non-blocking wrapper around an otherwise potentially mutable shared state object. They are inspired by agents in Clojure.</p><p>In our case we'll use an agent to "protect" a plain&nbsp;<span style="font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 9.6pt;">ArrayList</span>. For this simple case, we could have used some synchronized list, but in general, agents eliminate the need to find thread-safe implementation classes or indeed care at all about the thread safety of the underlying wrapped object.</p>
<pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#0033b3;">def </span><span style="color:#000000;">mutableState </span>= []     <span style="color:#8c8c8c;font-style:italic;">// a non-synchronized mutable list<br></span><span style="color:#0033b3;">def </span><span style="color:#000000;">agent </span>= <span style="color:#0033b3;">new </span><span style="color:#000000;">Agent</span>(<span style="color:#000000;">mutableState</span>)<br><br><span style="color:#000000;">agent</span>.attachToThreadPool(<span style="color:#0033b3;">new </span><span style="color:#000000;">VirtualPool</span>()) <span style="color:#8c8c8c;font-style:italic;">// omit line for normal threads<br></span><span style="color:#8c8c8c;font-style:italic;"><br></span><span style="color:#000000;">agent </span><span style="font-weight:bold;">{ </span>it &lt;&lt; <span style="color:#067d17;">'Dave' </span><span style="font-weight:bold;">}    </span><span style="color:#8c8c8c;font-style:italic;">// one thread updates list<br></span><span style="color:#000000;">agent </span><span style="font-weight:bold;">{ </span>it &lt;&lt; <span style="color:#067d17;">'Joe' </span><span style="font-weight:bold;">}     </span><span style="color:#8c8c8c;font-style:italic;">// another thread also updating<br></span><span style="color:#0033b3;">assert </span><span style="color:#000000;">agent</span>.val.size() == <span style="color:#1750eb;">2<br></span></pre>

<h3>Actors</h3>
<p>Actors allow for a message passing-based concurrency model. The actor model ensures that at most one thread processes the actor's body at any time. The GPars API and DSLs for actors are quite rich supporting many features. We'll look at a simple example here.</p><p>GPars manages actor thread pools in groups. Let's create one backed by virtual threads:</p>
<pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#0033b3;">def </span><span style="color:#000000;">vgroup </span>= <span style="color:#0033b3;">new </span><span style="color:#000000;">DefaultPGroup</span>(<span style="color:#0033b3;">new </span><span style="color:#000000;">VirtualPool</span>())
</pre>
<p>Now we can write an encrypting and decrypting actor pair as follows:</p>
<pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#0033b3;">def </span><span style="color:#000000;">decryptor </span>= <span style="color:#000000;">vgroup</span>.actor <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span>loop <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">        </span>react <span style="font-weight:bold;">{ </span><span style="color:#000000;">String </span>message <span style="font-weight:bold;">-&gt;<br></span><span style="font-weight:bold;">            </span>reply message.reverse()<br>        <span style="font-weight:bold;">}<br></span><span style="font-weight:bold;">    }<br></span><span style="font-weight:bold;">}<br></span><span style="font-weight:bold;"><br></span><span style="color:#0033b3;">def </span><span style="color:#000000;">console </span>= <span style="color:#000000;">vgroup</span>.actor <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#000000;">decryptor </span>&lt;&lt; <span style="color:#067d17;">'lellarap si yvoorG'<br></span><span style="color:#067d17;">    </span>react <span style="font-weight:bold;">{<br></span>        println <span style="color:#067d17;">'Decrypted message: ' </span>+ it<br>    <span style="font-weight:bold;">}<br></span><span style="font-weight:bold;">}<br></span><span style="font-weight:bold;"><br></span><span style="color:#000000;">console</span>.join() // output: Decrypted message: Groovy is parallel</pre>
<h3>Dataflow</h3>
<p style="">Dataflow&nbsp;offers an inherently safe and robust declarative concurrency model. Dataflows are also managed via thread groups, so we'll use&nbsp;<span style="font-family: &quot;JetBrains Mono&quot;, monospace; font-size: 14px;">vgroup</span>&nbsp;which we created earlier.</p><p style="">We have three logical tasks which can run in parallel and perform their work. The tasks need to exchange data and they do so using <i>dataflow variables</i>. Think of dataflow variables as one-shot channels safely and reliably transferring data from producers to their consumers.<br></p>
<pre style="color:#080808;font-family:'JetBrains Mono',monospace;font-size:9.6pt;"><span style="color:#0033b3;">def </span><span style="color:#000000;">df </span>= <span style="color:#0033b3;">new </span><span style="color:#000000;">Dataflows</span>()<br><br><span style="color:#000000;">vgroup</span>.task <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#000000;">df</span>.z = <span style="color:#000000;">df</span>.x + <span style="color:#000000;">df</span>.y<br><span style="font-weight:bold;">}<br></span><span style="font-weight:bold;"><br></span><span style="color:#000000;">vgroup</span>.task <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#000000;">df</span>.x = <span style="color:#1750eb;">10<br></span><span style="font-weight:bold;">}<br></span><span style="font-weight:bold;"><br></span><span style="color:#000000;">vgroup</span>.task <span style="font-weight:bold;">{<br></span><span style="font-weight:bold;">    </span><span style="color:#000000;">df</span>.y = <span style="color:#1750eb;">5<br></span><span style="font-weight:bold;">}<br></span><span style="font-weight:bold;"><br></span><span style="color:#0033b3;">assert </span><span style="color:#000000;">df</span>.z == <span style="color:#1750eb;">15<br></span></pre>
<p>The dataflow framework works out how to schedule the individual tasks and ensures that
a task's input variables are ready when needed.</p>
<h3>Conclusion</h3>
<p>We have had a quick glimpse at using virtual threads with Groovy and GPars.
It is very early days, so expect much more to emerge in this space once virtual
threads are released in preview in production versions of JDK19 and eventually beyond a preview feature.
</p>