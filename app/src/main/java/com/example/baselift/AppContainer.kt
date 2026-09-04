package com.example.baselift

import android.content.Context
import com.example.baselift.Model.local.AppDatabase

import com.example.baselift.Model.repository.UserRepository
import com.example.baselift.Model.repository.ProgressRepository
import com.example.baselift.Model.repository.NutritionRepository
import com.example.baselift.Model.repository.WorkoutRepository
import com.example.baselift.Model.repository.IUserRepository
import com.example.baselift.Model.repository.IProgressRepository
import com.example.baselift.Model.repository.INutritionRepository
import com.example.baselift.Model.repository.IWorkoutRepository

/**
 * Gere a injeção de dependências
 * Evita o uso de bibliotecas de injeção
 * Contentor central que instancia as dependências da aplicação
 *
 *  - É inicializado uma vez ao iniciar a app
 *  - Mantém referências globais como a base de dados
 *  - Expõe os repositórios e os DAOs necessários
 */
interface AppContainer {
    // Expõe a base de dados e os repositórios
    // para que possam ser injetados nas classes
    val database: AppDatabase
    val userRepository: IUserRepository
    val progressRepository: IProgressRepository
    val workoutRepository: IWorkoutRepository
    val nutritionRepository: INutritionRepository
}

/**
 * Implementação do AppContainer
 * Recebe o contexto e instancia as dependências
 */
class DefaultAppContainer(private val context: Context) : AppContainer {
    
    // lazy garante que a base de dados só é criada
    // na primeira vez que for necessária
    override val database: AppDatabase by lazy {
        AppDatabase.getInstance(context)
    }

    // Repositórios
    override val userRepository: IUserRepository by lazy {
        UserRepository(database.userDao())
    }

    override val progressRepository: IProgressRepository by lazy {
        ProgressRepository(database.weightLogDao(), database.photoLogDao())
    }

    override val workoutRepository: IWorkoutRepository by lazy {
        WorkoutRepository(database.workoutDao(), context)
    }

    override val nutritionRepository: INutritionRepository by lazy {
        NutritionRepository(database.nutritionDao())
    }
}
