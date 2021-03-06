/*
 * Copyright 2014-2015 ChalkPE
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package pe.chalk.takoyaki;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mozilla.javascript.*;
import pe.chalk.takoyaki.logger.Prefix;
import pe.chalk.takoyaki.logger.ConsoleLogger;
import pe.chalk.takoyaki.plugin.Plugin;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author ChalkPE <amato0617@gmail.com>
 * @since 2015-04-07
 */
public class Takoyaki implements Prefix {
    public static final String VERSION = "2.0";

    private static Takoyaki instance = null;

    private List<Target> targets;
    private List<Plugin> plugins;
    private ConsoleLogger logger;

    private boolean isAlive;

    public static Takoyaki getInstance(){
        return instance;
    }

    public Takoyaki() throws JSONException, IOException{
        Takoyaki.instance = this;
        this.logger = new ConsoleLogger(System.out);

        JSONObject properties;
        try{
            properties = new JSONObject(Files.lines(Paths.get("properties.json"), Charset.forName("UTF-8")).collect(Collectors.joining()));
        }catch(IOException e){
            this.getLogger().error("properties.json 파일을 읽을 수 없습니다 : " + e.getMessage());

            System.exit(1);
            return;
        }

        if(properties.has("options")){
            JSONObject optionsObject = properties.getJSONObject("options");
            this.getLogger().out = new PrintStream(new FileOutputStream(optionsObject.getString("output"), true));
        }

        JSONArray targetsArray = properties.getJSONArray("targets");
        this.targets = new ArrayList<>(targetsArray.length());
        for(int i = 0; i < targetsArray.length(); i++){
            this.targets.add(new Target(this, targetsArray.getJSONObject(i)));
        }

        File pluginDirectory = new File("plugins");
        if(!pluginDirectory.exists()){
            if(pluginDirectory.mkdir()){
                this.getLogger().debug("plugin 디렉토리를 생성했습니다");
            }
        }

        File[] pluginFiles = pluginDirectory.listFiles(file -> file.getName().endsWith(".js"));
        if(pluginFiles != null){
            this.plugins = new ArrayList<>(pluginFiles.length);
            for(File pluginFile : pluginFiles){
                try{
                    Plugin plugin = new Plugin(pluginFile);

                    this.getLogger().debug("플러그인 " + plugin.getName() + "을(를) 불러왔습니다");
                    this.plugins.add(plugin);
                }catch(JavaScriptException | IOException e){
                    this.getLogger().error(e.getMessage());
                }
            }
            this.plugins.forEach(plugin -> plugin.call("onCreate", new Object[]{plugin.getName()}));
            this.getLogger().newLine();
        }

        Runtime.getRuntime().addShutdownHook(new Thread(){
            @Override
            public void run(){
                Takoyaki.this.getLogger().newLine();
                Takoyaki.this.getLogger().debug("*** FINALIZATION RUNNING ***");
                Takoyaki.this.getLogger().newLine();

                Takoyaki.this.getPlugins().forEach(plugin -> plugin.call("onDistroy", Context.emptyArgs));
            }
        });
        this.isAlive = false;
    }

    public List<Target> getTargets(){
        return this.targets;
    }

    public Target getTarget(int clubId){
        for(Target target : this.getTargets()){
            if(target.getClubId() == clubId){
                return target;
            }
        }
        return null;
    }

    public List<Plugin> getPlugins(){
        return this.plugins;
    }

    public ConsoleLogger getLogger(){
        return this.logger;
    }

    public boolean isAlive(){
        return this.isAlive;
    }

    @Override
    public String getPrefix(){
        return "타코야키";
    }

    public void start(){
        this.isAlive = true;
        this.getTargets().forEach(Target::start);
    }

    public static void main(String[] args){
        try{
            new Takoyaki().start();
        }catch(JSONException | IOException e){
            e.printStackTrace();
            System.exit(1);
        }
    }
}