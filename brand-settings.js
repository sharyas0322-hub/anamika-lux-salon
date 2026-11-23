(function(){
  try{
    var link=document.createElement("link");
    link.rel="manifest";
    link.href="./manifest.webmanifest";
    document.head.appendChild(link);
    if("serviceWorker" in navigator){
      window.addEventListener("load",function(){
        navigator.serviceWorker.register("./sw.js",{scope:"./"}).catch(function(err){console.warn("Anamika PWA registration failed:",err);});
      });
    }
  }catch(e){console.warn("Anamika PWA setup failed:",e);}
})();