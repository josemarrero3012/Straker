package es.straker.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import androidx.lifecycle.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import java.math.BigDecimal
import java.text.NumberFormat
import java.util.Locale

val orange=Color(0xFFFF793B)
val ink=Color(0xFF151614)
val paper=Color(0xFFF6F4E9)
val panel=Color(0xFF242520)
val muted=Color(0xFFBABBAF)
val display=FontFamily(Font(R.font.display))
val body=FontFamily(Font(R.font.semibold))
fun eur(n:Long):String=NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-ES")).format(BigDecimal.valueOf(n,2))
fun odds(n:Long)=BigDecimal.valueOf(n,3).stripTrailingZeros().toPlainString().replace('.',',')
class MainActivity:ComponentActivity(){
    private val session:SessionViewModel by viewModels()
    override fun onCreate(savedInstanceState:Bundle?){
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme=darkColorScheme(primary=orange,onPrimary=ink,background=ink,surface=panel,onSurface=paper,onBackground=paper)){
                Surface(Modifier.fillMaxSize(),color=ink){
                    val owner by session.owner.collectAsStateWithLifecycle()
                    key(owner){
                        val id=owner
                        if(id==null) Welcome(session){session.google(this,it)}
                        else{
                            val vm:LedgerViewModel=viewModel(key=id,factory=remember(id){object:ViewModelProvider.Factory{
                                @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T =
                                    LedgerViewModel(LedgerRepository(applicationContext,id)) as T
                            }})
                            Dashboard(vm,if(id=="local")"Local" else "Google",session::logout)
                        }
                    }
                }
            }
        }
    }
}
@Composable fun Title(text:String,size:Int=34){Text(text.uppercase(Locale.forLanguageTag("es")),fontFamily=display,fontSize=size.sp,lineHeight=(size+2).sp)}
@Composable fun Caption(text:String){Text(text,color=muted,fontFamily=body,fontSize=14.sp)}
@Composable fun Brand(){Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.straker),null,Modifier.size(36.dp));Text("STRAKER",fontFamily=display,fontSize=29.sp)}}
@Composable fun CardBox(content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(panel).padding(16.dp),verticalArrangement=Arrangement.spacedBy(6.dp),content=content)
}
@Composable fun Welcome(session:SessionViewModel,google:(Boolean)->Unit){
    val nav= rememberNavController()
    NavHost(nav,"login",Modifier.safeDrawingPadding()){
        listOf("login","registro").forEach{route->composable(route){
            val register=route=="registro"
            val err by session.error.collectAsStateWithLifecycle();val busy by session.busy.collectAsStateWithLifecycle()
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),verticalArrangement=Arrangement.spacedBy(16.dp)){
                Brand()
                Image(painterResource(R.drawable.cancha),null,Modifier.fillMaxWidth().height(178.dp).clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)
                Title(if(register)"Crea tu perfil" else "Iniciar sesión",44)
                Caption(if(register)"Vincula tu cuenta de Google a un perfil de Straker en este dispositivo." else "Accede a tus bancas y a tu historial de apuestas.")
                Button({google(register)},Modifier.fillMaxWidth(),enabled=!busy){Text(if(busy)"Conectando…" else if(register)"Registrarme con Google" else "Continuar con Google")}
                if(err.isNotEmpty())Text(err,color=MaterialTheme.colorScheme.error)
                OutlinedButton({session.error.value="";if(register)nav.popBackStack() else nav.navigate("registro")},Modifier.fillMaxWidth(),enabled=!busy){Text(if(register)"Ya tengo un perfil" else "Crear un perfil")}
                TextButton(session::local,enabled=!busy){Text("Entrar en modo local")}
            }
        }}
    }
}
val tabs=linkedMapOf("inicio" to "Inicio","apuestas" to "Apuestas","bancas" to "Bancas","movimientos" to "Movimientos")
@Composable fun Dashboard(vm:LedgerViewModel,profile:String,logout:()->Unit){
    val nav=rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route=entry?.destination?.route
    val book by vm.book.collectAsStateWithLifecycle()
    val back={nav.popBackStack();Unit}
    fun go(path:String){vm.error.value="";nav.navigate(path){launchSingleTop=true}}
    Scaffold(containerColor=ink,topBar={Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Brand();TextButton(logout){Text("$profile · Salir")}
    }},bottomBar={if(route in tabs.keys)NavigationBar(containerColor=panel){
        tabs.entries.forEachIndexed{i,(path,label)-> NavigationBarItem(route==path,{
            nav.navigate(path){popUpTo("inicio"){saveState=true};launchSingleTop=true;restoreState=true}
        },icon={Text(listOf("⌂","▤","▣","⇄")[i],fontSize=22.sp,color=if(route==path)orange else muted)},label={Text(label,fontSize=11.sp,maxLines=1)})}
    }} ){padding->NavHost(nav,"inicio",Modifier.padding(padding)){
        composable("inicio"){Page("Mi panel"){
            val total=book.banks.sumOf{book.balance(it.id)}
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(orange),verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1.4f).padding(14.dp)){Text("SALDO DISPONIBLE",color=ink,fontFamily=body,fontSize=12.sp);Text(eur(total),color=ink,fontFamily=display,fontSize=35.sp);Text("Total en tus cuentas",color=ink,fontSize=12.sp)}
                Image(painterResource(R.drawable.cancha),null,Modifier.weight(1f).height(140.dp),contentScale=ContentScale.Crop)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                Box(Modifier.weight(1f)){CardBox{Caption("Resultado neto");Title(eur(book.bets.filter{it.status!="Pendiente"}.sumOf{it.payout()-it.stake}),23)}}
                Box(Modifier.weight(1f)){CardBox{Caption("En juego");Title(eur(book.bets.filter{it.status=="Pendiente"}.sumOf{it.stake}),23)}}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){Button({go("nueva-apuesta")}){Text("Nueva apuesta")};OutlinedButton({go("nuevo-movimiento")}){Text("Movimiento")}}
            Title("Últimas apuestas",25)
            if(book.bets.isEmpty())EmptyBets() else book.bets.take(4).forEach{BetRow(it,book){go("apuesta/"+it.id)}}
            Title("Mis bancas",25)
            if(book.banks.isEmpty())TextButton({go("nueva-banca")}){Text("Crear mi primera banca")} else book.banks.take(3).forEach{BankRow(it,book){go("cuenta/"+it.id)}}
        }}
        composable("bancas"){Page("Mis bancas"){Button({go("nueva-banca")}){Text("Nueva cuenta")};book.banks.forEach{BankRow(it,book){go("cuenta/"+it.id)}}}}
        composable("apuestas"){Page("Apuestas"){
            Button({go("nueva-apuesta")}){Text("Nueva apuesta")};var filter by rememberSaveable{mutableStateOf("Todas")}
            Choice("Estado",listOf("Todas")+outcomes,filter){filter=it}
            val rows=book.bets.filter{filter=="Todas" || it.status==filter}
            if(rows.isEmpty())EmptyBets() else rows.forEach{BetRow(it,book){go("apuesta/"+it.id)}}
        }}
        composable("movimientos"){Page("Movimientos"){
            Button({go("nuevo-movimiento")}){Text("Nuevo movimiento")};var filter by rememberSaveable{mutableStateOf("Todos")}
            Choice("Tipo",listOf("Todos")+moveTypes,filter){filter=it}
            val rows=book.moves.filter{filter=="Todos" || it.kind==filter || (filter=="Ingreso" && it.kind=="Saldo inicial")}
            if(rows.isEmpty())Caption("Sin movimientos.")
            rows.forEach{m->ClickableCard({go("movimiento/"+m.id)},m.kind+" · "+eur(m.amount),book.name(m.origin)+" → "+book.name(m.dest))}
        }}
        composable("nueva-banca"){FormPage("Nueva cuenta",vm,back){BankForm(vm,back)}}
        composable("nueva-apuesta"){FormPage("Nueva apuesta",vm,back){BetForm(vm,book,back){go("nueva-banca")}}}
        composable("nuevo-movimiento"){FormPage("Nuevo movimiento",vm,back){MoveForm(vm,book,back){go("nueva-banca")}}}
        composable("apuesta/{id}"){e->book.bets.find{it.id==e.arguments?.getString("id")}?.let{b->FormPage("Detalle de apuesta",vm,back){SettleForm(vm,b,book,back)}}}
        composable("cuenta/{id}"){e->book.banks.find{it.id==e.arguments?.getString("id")}?.let{a->Page(a.name,back){CardBox{Caption(if(a.book)a.house else "Cuenta bancaria");Title(eur(book.balance(a.id)));Caption("Saldo disponible")};book.bets.filter{it.bank==a.id}.forEach{BetRow(it,book){go("apuesta/"+it.id)}}}}}
        composable("movimiento/{id}"){e->book.moves.find{it.id==e.arguments?.getString("id")}?.let{m->Page(m.kind,back){CardBox{Title(eur(m.amount));Text(book.name(m.origin)+" → "+book.name(m.dest));Caption(java.text.DateFormat.getDateTimeInstance().format(java.util.Date(m.at)));if(m.note.isNotBlank())Text(m.note)}}}}
    }}
}
@Composable fun Page(title:String,back:(()->Unit)?=null,content:@Composable ColumnScope.()->Unit){
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
        if(back!=null)TextButton(back){Text("← Volver")};Title(title);content()
    }
}
@Composable fun ClickableCard(click:()->Unit,title:String,sub:String){Box(Modifier.fillMaxWidth().clickable(onClick=click)){CardBox{Text(title,fontFamily=body);Caption(sub)}}}
@Composable fun BankRow(a:Bank,b:Book,click:()->Unit)=ClickableCard(click,a.name+" · "+eur(b.balance(a.id)),if(a.book)a.house else "Cuenta bancaria")
@Composable fun BetRow(b:Bet,book:Book,click:()->Unit)=ClickableCard(click,b.event+" · "+eur(b.stake),b.status+" · "+book.name(b.bank)+" · Cuota "+odds(b.odds))
@Composable fun EmptyBets(){CardBox{Row(verticalAlignment=Alignment.CenterVertically){Image(painterResource(R.drawable.entradas),null,Modifier.size(54.dp));Spacer(Modifier.width(12.dp));Caption("Sin apuestas para mostrar.")}}}

