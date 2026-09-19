package com.bankomunal.config;

import com.bankomunal.entity.Course;
import com.bankomunal.entity.CourseLesson;
import com.bankomunal.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Siembra el catálogo de Educación Financiera la primera vez que se levanta
 * el backend con la tabla `cursos`.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourseSeeder implements CommandLineRunner {

    private final CourseRepository cursoRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (cursoRepository.count() > 0) {
            return; // ya hay cursos (sembrados antes, o creados por el admin)
        }

        cursoRepository.save(curso("ahorro", "💰", "#e8f4fb", "Fundamentos del Ahorro", "Básico", "45 min",
                "Aprende a construir el hábito del ahorro y alcanzar tus metas financieras paso a paso.", 100, 0,
                List.of(
                        leccion("¿Por qué ahorrar?",
                                "El ahorro es la base de la libertad financiera. Cuando ahorras, creas una red de seguridad que te protege ante imprevistos y te permite aprovechar oportunidades.\n\n**Regla del 50/30/20:** Dedica el 50% de tus ingresos a necesidades, 30% a deseos y 20% al ahorro.\n\nEmpieza con pequeñas cantidades. Ahorrar $5.000 diarios equivale a más de $1.800.000 al año.",
                                false, "¿Cuánto recomienda destinar la regla 50/30/20 al ahorro?",
                                List.of("10%", "15%", "20%", "25%"), 2),
                        leccion("Metas de ahorro",
                                "Una meta de ahorro clara multiplica tu motivación. Define:\n\n• **Qué** quieres lograr (fondo de emergencia, viaje, educación)\n• **Cuánto** necesitas exactamente\n• **Cuándo** lo quieres alcanzar\n\nDivide el monto total entre los meses disponibles para saber cuánto ahorrar cada mes.",
                                false, "¿Cuál es el primer paso para establecer una meta de ahorro?",
                                List.of("Abrir una cuenta bancaria", "Definir qué quieres lograr", "Pedir un préstamo",
                                        "Calcular tus gastos"),
                                1),
                        leccionFinal(
                                "Has aprendido los fundamentos del ahorro. Ahora tienes las herramientas para:\n\n Aplicar la regla 50/30/20\n Establecer metas de ahorro SMART\n Construir un fondo de emergencia\n\n¡Obtén tu certificado!"))));

        cursoRepository.save(curso("credito", "🏦", "#fef3e2", "Crédito Responsable", "Básico", "60 min",
                "Entiende cómo funcionan los créditos, las tasas de interés y cómo mantener un buen historial crediticio.",
                150, 1,
                List.of(
                        leccion("¿Qué es el crédito?",
                                "El crédito es una herramienta financiera que te permite usar dinero que no tienes hoy, con el compromiso de devolverlo en el futuro, más un interés.\n\n**Tipos de crédito:**\n• Crédito de consumo (compras)\n• Crédito hipotecario (vivienda)\n• Microcrédito (emprendimientos)\n• Crédito bancomunal (solidario)",
                                false, "¿Qué es el interés en un crédito?",
                                List.of("Un regalo del banco", "El costo por usar dinero prestado", "Un descuento",
                                        "Un impuesto"),
                                1),
                        leccion("Historial crediticio",
                                "Tu historial crediticio es tu \"reputación financiera\". Los bancos lo consultan antes de prestarte dinero.\n\n**Factores que lo afectan:**\n• Pagar a tiempo \n• No sobrepasar el 30% del cupo de tu tarjeta \n• No solicitar muchos créditos seguidos \n\nUn buen historial te da acceso a mejores tasas.",
                                false, "¿Cuál es la principal forma de mejorar tu historial crediticio?",
                                List.of("Solicitar más tarjetas", "Pagar a tiempo siempre", "Cerrar todas tus deudas",
                                        "No usar crédito"),
                                1),
                        leccion("Simulación de préstamos",
                                "Antes de tomar un crédito, simúlalo. Considera:\n\n• **Tasa de interés mensual:** en Bankomunal es del 2% mensual\n• **Plazo:** el tiempo que tienes para pagar\n• **Cuota mensual:** lo que pagas cada mes\n\nUsa nuestro simulador para calcular cuánto pagarás en total antes de comprometerte.",
                                false, "Si pides $1.000.000 a 6 meses al 2% mensual, ¿cuál es la cuota aproximada?",
                                List.of("$150.000", "$178.526", "$200.000", "$166.000"), 1),
                        leccionFinal(
                                "Ahora eres un experto en crédito responsable. Recuerda:\n\n Usa el crédito como herramienta, no como ingreso\n Simula siempre antes de solicitar\n Paga puntualmente para construir buen historial\n\n¡Obtén tu certificado!"))));

        cursoRepository.save(curso("presupuesto", "📊", "#f0f7ff", "Presupuesto Personal", "Intermedio", "50 min",
                "Construye un presupuesto mensual que funcione para ti y toma control de tus finanzas.", 120, 2,
                List.of(
                        leccion("Ingresos y gastos",
                                "El primer paso es conocer exactamente cuánto entra y cuánto sale.\n\n**Ingresos:** salario, arriendos, ventas, remesas\n**Gastos fijos:** arriendo, servicios, cuotas\n**Gastos variables:** alimentación, transporte, ocio\n\nDurante un mes anota TODOS tus gastos, por pequeños que sean.",
                                false, "¿Cuál de estos es un gasto fijo?",
                                List.of("Cine", "Mercado", "Arriendo", "Restaurante"), 2),
                        leccion("Construir el presupuesto",
                                "Con tus ingresos y gastos identificados:\n\n1. Lista todos los ingresos del mes\n2. Resta los gastos fijos obligatorios\n3. Asigna una cantidad al ahorro (20% mínimo)\n4. El resto es para gastos variables\n\n**Si gastas más de lo que ganas:** identifica qué gastos puedes reducir.",
                                false, "¿Qué debes hacer primero al construir un presupuesto?",
                                List.of("Ahorrar", "Pagar deudas", "Identificar ingresos y gastos", "Invertir"), 2),
                        leccionFinal(
                                "¡Excelente! Ahora sabes cómo:\n\n Registrar ingresos y gastos\n Identificar gastos eliminables\n Construir un presupuesto equilibrado\n\n¡Obtén tu certificado!"))));

        cursoRepository.save(curso("inversion", "📈", "#f3eefa", "Inversión para Principiantes", "Avanzado", "75 min",
                "Descubre cómo hacer crecer tu dinero con conceptos básicos de inversión colectiva y bankomunal.", 200,
                3,
                List.of(
                        leccion("¿Qué es invertir?",
                                "Invertir es poner tu dinero a trabajar para generar más dinero. A diferencia del ahorro (que protege), la inversión busca crecer.\n\n**Riesgo vs. rentabilidad:** mayor rentabilidad esperada = mayor riesgo.\n\nEn Bankomunal el fondo común actúa como una inversión colectiva: todos aportan y todos se benefician de los intereses de los préstamos.",
                                false, "¿Cuál es la diferencia principal entre ahorrar e invertir?",
                                List.of("Son lo mismo", "Invertir busca crecer el dinero", "Ahorrar es más rentable",
                                        "Invertir no tiene riesgo"),
                                1),
                        leccion("El poder del interés compuesto",
                                "El interés compuesto es ganar intereses sobre tus intereses.\n\n**Ejemplo:** Si inviertes $1.000.000 al 2% mensual:\n• Mes 1: $1.020.000\n• Mes 6: $1.126.162\n• Mes 12: $1.268.242\n\nEinstein lo llamó \"la octava maravilla del mundo\". Empieza cuanto antes.",
                                false, "¿En qué consiste el interés compuesto?",
                                List.of("Interés sobre el capital inicial", "Interés sobre intereses acumulados",
                                        "Un tipo de crédito", "Un seguro"),
                                1),
                        leccion("Fondos comunes en Bankomunal",
                                "El fondo común de tu grupo es una forma de inversión colectiva:\n\n• Cada miembro aporta periódicamente\n• Los fondos se usan para préstamos internos\n• Los intereses cobrados regresan al fondo\n• Todos los socios se benefician\n\nEs inversión solidaria: ganas tú, gana tu comunidad.",
                                false, "¿De dónde provienen los rendimientos del fondo común?",
                                List.of("Del banco", "De los intereses de préstamos internos", "Del gobierno",
                                        "De donaciones"),
                                1),
                        leccionFinal(
                                "¡Felicitaciones! Ahora entiendes:\n\n La diferencia entre ahorro e inversión\n El poder del interés compuesto\n Cómo funciona el fondo común de Bankomunal\n\n¡Obtén tu certificado!"))));

        cursoRepository.save(curso("ahorro", "🎯", "#e8f8ee", "Fondo de Emergencia", "Básico", "30 min",
                "Aprende a construir un colchón financiero que te proteja de los imprevistos de la vida.", 80, 4,
                List.of(
                        leccion("¿Para qué sirve?",
                                "Un fondo de emergencia es dinero líquido guardado exclusivamente para situaciones imprevistas: pérdida de empleo, enfermedad, reparación urgente.\n\n**Meta recomendada:** 3 a 6 meses de gastos básicos.\n\nSin este fondo, cualquier imprevisto puede llevarte a endeudarte.",
                                false, "¿Cuántos meses de gastos debe cubrir un fondo de emergencia?",
                                List.of("1 mes", "3 a 6 meses", "12 meses", "No importa"), 1),
                        leccionFinal(
                                "¡Muy bien! Ahora sabes que:\n\n Un fondo de emergencia es tu primera prioridad financiera\n Debe cubrir entre 3 y 6 meses de gastos\n Debe estar líquido, no invertido\n\n¡Obtén tu certificado!"))));

        log.info("CourseSeeder: catálogo de educación financiera sembrado (5 cursos).");
    }

    private Course curso(String cat, String emoji, String color, String titulo, String nivel, String duracion,
            String desc, int puntos, int orden, List<CourseLesson> lecciones) {
        Course c = Course.builder()
                .categoria(cat).emoji(emoji).color(color).titulo(titulo).nivel(nivel).duracion(duracion)
                .descripcion(desc).puntos(puntos).orden(orden).activo(true)
                .lecciones(new ArrayList<>())
                .build();
        int i = 0;
        for (CourseLesson l : lecciones) {
            l.setCurso(c);
            l.setOrden(i++);
            c.getLecciones().add(l);
        }
        return c;
    }

    private CourseLesson leccion(String titulo, String contenido, boolean esFinal, String pregunta, List<String> opciones,
            int correcta) {
        return CourseLesson.builder()
                .titulo(titulo).contenido(contenido).esFinal(esFinal)
                .quizPregunta(pregunta).quizOpciones(new ArrayList<>(opciones)).quizCorrecta(correcta)
                .build();
    }

    private CourseLesson leccionFinal(String contenido) {
        return CourseLesson.builder()
                .titulo("🏆 ¡Curso completado!").contenido(contenido).esFinal(true)
                .quizOpciones(new ArrayList<>())
                .build();
    }
}