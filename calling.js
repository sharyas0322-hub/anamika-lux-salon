async function callMaskedBooking(bookingId,direction,button){
  if(!bookingId)return;
  if(button){
    button.disabled=true;
    button.dataset.originalText=button.innerHTML;
    button.innerHTML='<i class="fa-solid fa-phone-volume"></i> Connecting...';
  }
  try{
    const result=await db.functions.invoke("edesy-masked-call",{body:{booking_id:bookingId,direction}});
    if(result.error){
      const detail=result.data?.error||result.error.message||"Call could not be started.";
      throw new Error(detail);
    }
    if(result.data?.error)throw new Error(result.data.error);
    const masked=result.data?.masked_number?(" Masked number: "+result.data.masked_number):"";
    alert("Masked call started."+masked+" The caller's phone will ring first, then the other party will be connected.");
  }catch(e){
    alert("Call start nahi hua: "+(e?.message||"Unknown error"));
  }finally{
    if(button){
      button.disabled=false;
      button.innerHTML=button.dataset.originalText||'<i class="fa-solid fa-phone"></i> Call';
    }
  }
}

document.addEventListener("click",function(event){
  const button=event.target.closest("[data-masked-call]");
  if(!button)return;
  callMaskedBooking(button.dataset.bookingId,button.dataset.direction,button);
});
