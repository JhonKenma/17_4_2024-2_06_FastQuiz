package com.secure.fastquiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secure.fastquiz.dtos.AlternativaDTO;
import com.secure.fastquiz.dtos.CuestionarioDTO;
import com.secure.fastquiz.dtos.PreguntaSeleccionadaDTO;
import com.secure.fastquiz.models.*;
import com.secure.fastquiz.repositories.*;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
public class CuestionarioService {

    @Autowired
    private CuestionarioRepository cuestionarioRepository;

    @Autowired
    private PreguntaSeleccionadaRepository preguntaSeleccionadaRepository;

    @Autowired
    private PublicacionCuestionarioRepository publicacionCuestionarioRepository;

    @Autowired
    private PreguntaRepository preguntaRepository;

    @Autowired
    private AlternativaRepository alternativaRepository;

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PreguntaService preguntaService;  // Añadir esta línea para inyectar el servicio


    // Inicializa objectMapper como atributo de la clase
    private final ObjectMapper objectMapper = new ObjectMapper();
    // Método para crear un cuestionario y asociarlo al usuario autenticado
    public Cuestionario crearCuestionario(String titulo, String descripcion, Long preguntaId, List<String> clavesPreguntas, List<Integer> tiempos, List<Boolean> requeridos) {
        // Obtener el usuario autenticado
        User usuario = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Crear el cuestionario y asociarlo con el usuario
        Cuestionario cuestionario = new Cuestionario();
        cuestionario.setTitulo(titulo);
        cuestionario.setDescripcion(descripcion);
        cuestionario.setUsuario(usuario);  // Asociar el usuario al cuestionario

        // Guardar el cuestionario primero para asegurarse de que está persistido
        cuestionario = cuestionarioRepository.save(cuestionario);

        // Buscar la pregunta general en la base de datos (la que contiene el JSON)
        Pregunta pregunta = preguntaRepository.findById(preguntaId)
                .orElseThrow(() -> new EntityNotFoundException("Pregunta no encontrada"));

        try {
            // Obtener y convertir el JSON en un Map
            String preguntasJson = pregunta.getPreguntasJson();
            Map<String, String> preguntasMap = objectMapper.readValue(preguntasJson, Map.class);

            // Procesar las claves solicitadas para extraer las preguntas específicas
            for (int i = 0; i < clavesPreguntas.size(); i++) {
                String clave = clavesPreguntas.get(i);
                String preguntaTexto = preguntasMap.get(clave); // Obtener la pregunta específica por su clave
                if (preguntaTexto != null) { // Verificar si la clave existe en el JSON
                    // Crear una nueva instancia de PreguntaSeleccionada
                    PreguntaSeleccionada preguntaSeleccionada = new PreguntaSeleccionada();
                    preguntaSeleccionada.setCuestionario(cuestionario);
                    preguntaSeleccionada.setPregunta(pregunta); // Relacionar con la entidad Pregunta
                    preguntaSeleccionada.setTextoPreguntaSeleccionada(preguntaTexto); // Guardar el texto específico de la pregunta

                    // Obtener las alternativas de la pregunta seleccionada
                    Map<String, String> alternativas = preguntaService.obtenerAlternativasDePregunta(preguntaId, Integer.parseInt(clave.split(" ")[1])); // Obtener alternativas para la pregunta

                    // Convertir las alternativas en JSON
                    String alternativasJson = objectMapper.writeValueAsString(alternativas);

                    // Guardar las alternativas como JSON en la entidad PreguntaSeleccionada
                    preguntaSeleccionada.setAlternativasJson(alternativasJson);

                    // Establecer el tiempo y requerido de la pregunta
                    preguntaSeleccionada.setTiempo(tiempos.get(i));  // Asignar tiempo de la lista
                    preguntaSeleccionada.setRequerido(requeridos.get(i)); // Asignar requerido de la lista

                    // Guardar la pregunta seleccionada en la base de datos
                    preguntaSeleccionadaRepository.save(preguntaSeleccionada);
                    cuestionario.getPreguntasSeleccionadas().add(preguntaSeleccionada);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error al procesar el JSON de preguntas", e);
        }

        return cuestionario;
    }

    //OBTENEMOS EL CUESTIONARIO ESTRUCTURADO, ES DECIR CON SUS PREGUNTAS Y ALTERNATIVAS
    public CuestionarioDTO obtenerCuestionarioConPreguntasYAlternativas(Long cuestionarioId) {
        CuestionarioDTO cuestionarioDTO = new CuestionarioDTO();

        // Obtener el cuestionario desde la base de datos
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado"));

        cuestionarioDTO.setId(cuestionario.getId());
        cuestionarioDTO.setTitulo(cuestionario.getTitulo());
        cuestionarioDTO.setDescripcion(cuestionario.getDescripcion());

        // Crear una lista para las preguntas seleccionadas
        List<PreguntaSeleccionadaDTO> preguntasSeleccionadasDTO = new ArrayList<>();

        // Iterar sobre las preguntas seleccionadas del cuestionario
        for (PreguntaSeleccionada preguntaSeleccionada : cuestionario.getPreguntasSeleccionadas()) {
            PreguntaSeleccionadaDTO preguntaSeleccionadaDTO = new PreguntaSeleccionadaDTO();
            preguntaSeleccionadaDTO.setId(preguntaSeleccionada.getId());
            preguntaSeleccionadaDTO.setTextoPreguntaSeleccionada(preguntaSeleccionada.getTextoPreguntaSeleccionada());

            // Agregar los nuevos campos: tiempo y requerido
            preguntaSeleccionadaDTO.setTiempo(preguntaSeleccionada.getTiempo());
            preguntaSeleccionadaDTO.setRequerido(preguntaSeleccionada.getRequerido());

            // Obtener las alternativas almacenadas en JSON
            String alternativasJson = preguntaSeleccionada.getAlternativasJson();

            try {
                // Intentar deserializar el JSON de alternativas como una lista de mapas
                List<Map<String, String>> alternativasList = null;

                // Primero intentamos deserializar como lista de objetos
                try {
                    alternativasList = objectMapper.readValue(alternativasJson, new TypeReference<List<Map<String, String>>>() {});
                } catch (Exception e) {
                    // Si falla, probamos como un solo objeto
                    Map<String, String> alternativaMap = objectMapper.readValue(alternativasJson, Map.class);
                    alternativasList = new ArrayList<>();
                    alternativasList.add(alternativaMap);  // Agregamos el único objeto a la lista
                }

                // Convertir cada alternativa en un DTO
                List<AlternativaDTO> alternativasDTO = new ArrayList<>();
                for (Map<String, String> alternativa : alternativasList) {
                    for (Map.Entry<String, String> entry : alternativa.entrySet()) {
                        AlternativaDTO alternativaDTO = new AlternativaDTO();
                        alternativaDTO.setClave(entry.getKey());
                        alternativaDTO.setTexto(entry.getValue());
                        alternativasDTO.add(alternativaDTO);
                    }
                }

                preguntaSeleccionadaDTO.setAlternativas(alternativasDTO);
            } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException("Error al procesar las alternativas JSON", e);
            }

            preguntasSeleccionadasDTO.add(preguntaSeleccionadaDTO);
        }

        cuestionarioDTO.setPreguntasSeleccionadas(preguntasSeleccionadasDTO);

        return cuestionarioDTO;
    }

    //METODO PARA EDITAR UN CUESTIONARIO
    /*
     *  Esto puede involucrar agregar, eliminar o actualizar preguntas.*/
    public Cuestionario editarCuestionario(Long id, String nuevoTitulo, String nuevaDescripcion, List<Long> preguntasSeleccionadasIds, List<Integer> nuevosTiempos, List<Boolean> nuevosRequeridos) {
        // Buscar el cuestionario existente
        Cuestionario cuestionario = cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado"));

        // Actualizar título y descripción
        cuestionario.setTitulo(nuevoTitulo);
        cuestionario.setDescripcion(nuevaDescripcion);

        // Iterar sobre las preguntas seleccionadas y actualizarlas
        List<PreguntaSeleccionada> preguntasSeleccionadas = cuestionario.getPreguntasSeleccionadas();
        for (int i = 0; i < preguntasSeleccionadas.size(); i++) {
            PreguntaSeleccionada preguntaSeleccionada = preguntasSeleccionadas.get(i);

            // Actualizar detalles de las preguntas
            preguntaSeleccionada.setTiempo(nuevosTiempos.get(i));
            preguntaSeleccionada.setRequerido(nuevosRequeridos.get(i));
            preguntaSeleccionadaRepository.save(preguntaSeleccionada);
        }

        // Guardar y devolver el cuestionario actualizado
        return cuestionarioRepository.save(cuestionario);
    }

    //Metodo Eliminar un cuestionario completo
    public void eliminarCuestionario(Long id) {
        Cuestionario cuestionario = cuestionarioRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado"));

        cuestionarioRepository.delete(cuestionario);
    }
    //Metodo para Eliminar una pregunta seleccionada específica
    public void eliminarPreguntaSeleccionada(Long id) {
        PreguntaSeleccionada preguntaSeleccionada = preguntaSeleccionadaRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Pregunta seleccionada no encontrada"));

        preguntaSeleccionadaRepository.delete(preguntaSeleccionada);
    }

    /*Metodo para publicaar el cuestionario*/
    public PublicacionCuestionario publicarCuestionario(Long cuestionarioId, LocalDateTime fechaInicio, LocalDateTime fechaFin, String urlPublica) {
        // Obtener el usuario autenticado
        User usuario = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        // Obtener el cuestionario
        Cuestionario cuestionario = cuestionarioRepository.findById(cuestionarioId)
                .orElseThrow(() -> new EntityNotFoundException("Cuestionario no encontrado"));

        // Crear la publicación del cuestionario
        PublicacionCuestionario publicacion = new PublicacionCuestionario();
        publicacion.setCuestionario(cuestionario);
        publicacion.setFechaInicio(fechaInicio);
        publicacion.setFechaFin(fechaFin);
        publicacion.setUrlPublica(urlPublica);
        publicacion.setUsuario(usuario);  // Asocia la publicación al usuario autenticado

        // Guardar la publicación
        return publicacionCuestionarioRepository.save(publicacion);
    }


}
