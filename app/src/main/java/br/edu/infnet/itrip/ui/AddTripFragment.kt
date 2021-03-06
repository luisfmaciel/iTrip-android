package br.edu.infnet.itrip.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.PermissionChecker
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.navigation.fragment.findNavController
import br.edu.infnet.itrip.AsyncTask.EncodeImageAyncTask
import br.edu.infnet.itrip.AsyncTask.DecodeImageAsyncTsk
import br.edu.infnet.itrip.ENCODE_IMAGE
import br.edu.infnet.itrip.Model.Trip
import br.edu.infnet.itrip.R
import br.edu.infnet.itrip.ViewModel.TripViewModel
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.android.synthetic.main.fragment_add_trip.*

class AddTripFragment : Fragment() {

    private lateinit var tripViewModel: TripViewModel
    private var firestoreDB: FirebaseFirestore? = null
    private lateinit var auth: FirebaseAuth
    private val GRANTED = PermissionChecker.PERMISSION_GRANTED
    private val CAMERA  = Manifest.permission.CAMERA
    private val REQUEST_IMAGE_CAPTURE = 1001
    private val REQUEST_PERMISSION_CODE = 1009
    private var imageBitmap: Bitmap? = null
    private var encodedImageString = ""
    private val TAG = "AddTripFragment"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        auth = FirebaseAuth.getInstance()
        firestoreDB = FirebaseFirestore.getInstance()
        return inflater.inflate(R.layout.fragment_add_trip, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        activity?.let { act ->
            tripViewModel = ViewModelProviders.of(act)
                .get(TripViewModel::class.java)
        }

        img_camera_add_trip.visibility = View.VISIBLE

        changeImage()
        fillFieldsData()
        setupListeners()

    }

    private fun setupListeners() {

        var ratingTrip = "0"
        rb_trip_add.setOnRatingBarChangeListener { ratingBar, rating, fromUser ->
            tv_rating_trip.text = rating.toString()
            ratingTrip = rating.toString()
        }

        fab_confirm_add_trip.setOnClickListener {

            val idTrip = tripViewModel.tripVM.value?.id
            val countryTrip = et_country_trip.text.toString()
            val dateTrip = et_date_trip.text.toString()
            val photoTrip = ENCODE_IMAGE
            val descriptionTrip = et_description_trip.text.toString()

            if (ENCODE_IMAGE.isNotEmpty() && countryTrip.isNotEmpty() &&
                dateTrip.isNotEmpty() && photoTrip.isNotEmpty() &&
                descriptionTrip.isNotEmpty()) {

                    val trip = Trip(
                        idTrip,
                        countryTrip,
                        dateTrip,
                        photoTrip,
                        descriptionTrip,
                        ratingTrip
                    )

                saveInFirestore(trip)
                tripViewModel.tripVM.value = null
                findNavController().navigate(R.id.action_addTripFragment_to_myTripsFragment, null)

            } else {
                Toast.makeText(context, getString(R.string.fill_all_fields), Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun fillFieldsData(){
        tripViewModel.tripVM.observe(viewLifecycleOwner, Observer {
            if (it != null) {

                img_camera_add_trip.visibility = View.GONE
                et_country_trip.setText(it.countryTrip)
                et_date_trip.setText(it.dateTrip)
                et_description_trip.setText(it.descriptionTrip)
                rb_trip_add.rating = it.ratingTrip.toFloat()
                tv_rating_trip.text = it.ratingTrip
                ENCODE_IMAGE = it.photoTrip

                DecodeImageAsyncTsk(add_photo_trip).execute(ENCODE_IMAGE)

            }
        })
    }

    private fun addTripFirestore(trip: Trip) {
        firestoreDB!!.collection("Users").document(auth.currentUser!!.uid)
            .collection("Trips")
            .add(trip.toMap())
            .addOnSuccessListener { documentReference ->
                Log.e(TAG, "DocumentSnapshot written with ID: " + documentReference.id)
                Toast.makeText(context, "Trip has been added!", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error adding Trip document", e)
                Toast.makeText(context, "Trip could not be added!", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateTripFirestore(trip: Trip) {
        trip.id?.let {
            firestoreDB!!.collection("Users").document(auth.currentUser!!.uid)
                .collection("Trips")
                .document(it)
                .set(trip.toMap())
                .addOnSuccessListener {
                    Log.e(TAG, "Trip document update successful!")
                    Toast.makeText(context, "Trip has been updated!", Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener { e ->
                    Log.e(TAG, "Error adding Trip document", e)
                    Toast.makeText(context, "Trip could not be updated!", Toast.LENGTH_SHORT).show()
                }
        }
    }

    private fun saveInFirestore(trip: Trip) {
        if (trip.id == null) {
            addTripFirestore(trip)
        } else {
            updateTripFirestore(trip)
        }
    }

    private fun changeImage() {
        add_photo_trip.setOnClickListener {
            when {
                PermissionChecker.checkSelfPermission(requireContext(), CAMERA) == GRANTED -> captureImage()
                shouldShowRequestPermissionRationale(CAMERA) -> showDialogPermission(
                    "It is necessary to release access to the camera",
                    arrayOf(CAMERA)
                )
                else -> requestPermissions(
                    arrayOf(CAMERA),
                    REQUEST_IMAGE_CAPTURE
                )
            }
        }
    }

    private fun captureImage(){
        val capturaImagemIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        try {
            startActivityForResult(capturaImagemIntent, REQUEST_IMAGE_CAPTURE)
        } catch (e: Exception) {
            Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == Activity.RESULT_OK) {
            if (requestCode == REQUEST_IMAGE_CAPTURE) {
                try {
                    img_camera_add_trip.visibility = View.GONE
                    imageBitmap = data!!.extras!!["data"] as Bitmap?
                    add_photo_trip.setImageBitmap(imageBitmap)

                    EncodeImageAyncTask(imageBitmap!!).execute()

                } catch (e: Exception) {
                    Toast.makeText(context, "${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showDialogPermission(
        message: String, permissions: Array<String>
    ) {
        val alertDialog = AlertDialog
            .Builder(requireContext())
            .setTitle("Permissions")
            .setMessage(message)
            .setPositiveButton("Ok") { dialog, _ ->
                requestPermissions(
                    permissions,
                    REQUEST_PERMISSION_CODE)
                dialog.dismiss()
            }.setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
            }
        alertDialog.show()
    }

}